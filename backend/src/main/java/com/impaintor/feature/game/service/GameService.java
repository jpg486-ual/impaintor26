package com.impaintor.feature.game.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.impaintor.feature.game.model.GameState;
import com.impaintor.feature.game.model.GameState.CanvasSnapshot;
import com.impaintor.feature.game.model.GalleryPhaseEvent;
import com.impaintor.feature.realtime.dto.outbound.RoleAssignment;
import com.impaintor.feature.realtime.service.RealtimePublisher;
import com.impaintor.feature.room.models.Room;
import com.impaintor.feature.room.repository.RoomRepository;
import com.impaintor.feature.user.models.User;
import com.impaintor.feature.wordgroup.models.WordGroup;
import com.impaintor.feature.wordgroup.repositories.WordGroupRepository;
import com.impaintor.feature.realtime.dto.outbound.GameEvent;

/**
 * Inicializa y conserva el estado en memoria de una partida por sala.
 */
@Service
public class GameService {

    private static final String GAME_TOPIC = "/topic/room.%s.game";

    private final RoomRepository roomRepository;
    private final WordGroupRepository wordGroupRepository;
    private final RealtimePublisher realtimePublisher;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public GameService(RoomRepository roomRepository,
                       WordGroupRepository wordGroupRepository,
                       RealtimePublisher realtimePublisher,
                       SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.wordGroupRepository = wordGroupRepository;
        this.realtimePublisher = realtimePublisher;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public GameState initializeGame(String roomCode) {
        GameState existing = activeGames.get(roomCode);
        if (existing != null) {
            return existing;
        }

        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("No existe la sala " + roomCode));

        List<User> players = room.getPlayersNames();
        if (players == null || players.size() < 3) {
            throw new IllegalStateException("Se necesitan al menos 3 jugadores para iniciar la partida");
        }

        WordGroup wordGroup = resolveWordGroup(room);
        List<String> candidateWords = new ArrayList<>();
        candidateWords.add(wordGroup.getWord1());
        candidateWords.add(wordGroup.getWord2());
        candidateWords.add(wordGroup.getWord3());
        Collections.shuffle(candidateWords);

        String secretWord = candidateWords.get(0);
        String hintWord = candidateWords.get(1);

        List<User> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);
        User impostor = shuffledPlayers.get(0);

        List<Long> drawingOrder = new ArrayList<>();
        for (User player : shuffledPlayers) {
            drawingOrder.add(player.getId());
        }

        GameState gameState = new GameState(drawingOrder, extractPlayerIds(players));
        gameState.setPhase(GameState.Phase.DRAWING);
        gameState.setRound(1);
        gameState.setWordGroup(wordGroup);
        gameState.setSecretWord(secretWord);
        gameState.setHintWord(hintWord);
        gameState.setImpostorId(impostor.getId());

        activeGames.put(roomCode, gameState);

        realtimePublisher.publishGameEvent(roomCode, new GameEvent.GameStart(drawingOrder, 1));

        // start the first turn cycle
        startNextTurn(roomCode);

        room.setWordGroup(wordGroup);
        room.setSecretWord(secretWord);
        room.setHintWord(hintWord);
        room.setGameState(Room.GameState.PLAYING);
        roomRepository.save(room);

        sendRoleAssignments(players, gameState);
        return gameState;
    }

    public Optional<GameState> findActiveGame(String roomCode) {
        return Optional.ofNullable(activeGames.get(roomCode));
    }

    public void clearGame(String roomCode) {
        activeGames.remove(roomCode);
    }

    private WordGroup resolveWordGroup(Room room) {
        WordGroup roomGroup = room.getWordGroup();
        if (roomGroup != null) {
            return roomGroup;
        }

        return wordGroupRepository.findRandom()
                .orElseThrow(() -> new IllegalStateException("No hay grupos de palabras disponibles"));
    }

    private List<Long> extractPlayerIds(List<User> players) {
        List<Long> ids = new ArrayList<>();
        for (User player : players) {
            ids.add(player.getId());
        }
        return ids;
    }

    private void sendRoleAssignments(List<User> players, GameState gameState) {
        for (User player : players) {
            Long playerId = player.getId();
            if (playerId != null && playerId.equals(gameState.getImpostorId())) {
                realtimePublisher.sendRoleAssignment(playerId,
                        new RoleAssignment.Impostor(gameState.getHintWord(), 0));
            } else {
                realtimePublisher.sendRoleAssignment(playerId,
                        new RoleAssignment.Painter(gameState.getSecretWord()));
            }
        }
    }

    public void recordCanvasSnapshot(String roomCode, Long playerId, String dataUrl) {
        GameState gs = activeGames.get(roomCode);
        if (gs == null) return;
        gs.recordCanvasSnapshot(playerId, dataUrl);
    }

    // --- Turn management ---
    private void startNextTurn(String roomCode) {
        GameState gs = activeGames.get(roomCode);
        if (gs == null) return;

        synchronized (gs) {
            Long drawer = gs.getCurrentDrawer();
            if (drawer == null) {
                // no more drawers this round -> gallery phase
                enterGalleryPhase(roomCode);
                return;
            }

            int timeSeconds = roomRepository.findByRoomCode(roomCode)
                    .map(Room::getDrawTime)
                    .filter(d -> d != null && d > 0)
                    .orElse(30);

            gs.setPhase(GameState.Phase.DRAWING);
            realtimePublisher.publishGameEvent(roomCode, new GameEvent.TurnStart(drawer, timeSeconds, gs.getDrawingOrder()));

            // schedule end of turn
            ScheduledFuture<?> f = scheduler.schedule(() -> endTurn(roomCode, drawer), timeSeconds, TimeUnit.SECONDS);
            ScheduledFuture<?> prev = scheduledTasks.put(roomCode, f);
            if (prev != null) prev.cancel(false);
        }
    }

    private void endTurn(String roomCode, Long drawerId) {
        GameState gs = activeGames.get(roomCode);
        if (gs == null) return;

        synchronized (gs) {
            // cancel stored task reference
            ScheduledFuture<?> scheduled = scheduledTasks.remove(roomCode);
            if (scheduled != null) scheduled.cancel(false);

            realtimePublisher.publishGameEvent(roomCode, new GameEvent.TurnEnd(drawerId));

            // advance to next drawer that is alive
            gs.advanceDrawer();
            Long next = gs.getCurrentDrawer();
            while (next != null && !gs.isPlayerAlive(next)) {
                gs.advanceDrawer();
                next = gs.getCurrentDrawer();
            }

            if (next == null) {
                // everyone drew -> gallery
                enterGalleryPhase(roomCode);
                return;
            }

            // start next turn
            startNextTurn(roomCode);
        }
    }

    private void enterGalleryPhase(String roomCode) {
        GameState gs = activeGames.get(roomCode);
        if (gs == null) return;

        synchronized (gs) {
            ScheduledFuture<?> scheduled = scheduledTasks.remove(roomCode);
            if (scheduled != null) scheduled.cancel(false);

            gs.setPhase(GameState.Phase.GALLERY);

            List<CanvasSnapshot> snapshots = new ArrayList<>();
            for (Long playerId : gs.getDrawingOrder()) {
                String dataUrl = gs.getCanvasSnapshots().get(playerId);
                if (dataUrl != null) {
                    snapshots.add(new CanvasSnapshot(playerId, dataUrl));
                }
            }
            for (Map.Entry<Long, String> entry : gs.getCanvasSnapshots().entrySet()) {
                boolean alreadyAdded = snapshots.stream().anyMatch(snapshot -> snapshot.playerId().equals(entry.getKey()));
                if (!alreadyAdded) {
                    snapshots.add(new CanvasSnapshot(entry.getKey(), entry.getValue()));
                }
            }

            messagingTemplate.convertAndSend(GAME_TOPIC.formatted(roomCode), new GalleryPhaseEvent(snapshots));

            scheduleVotePhase(roomCode, gs);
        }
    }

    private void scheduleVotePhase(String roomCode, GameState gs) {
        int gallerySeconds = roomRepository.findByRoomCode(roomCode)
                .map(Room::getDrawTime)
                .filter(d -> d != null && d > 0)
                .orElse(30) / 2;

        if (gallerySeconds < 5) {
            gallerySeconds = 5;
        }

        ScheduledFuture<?> f = scheduler.schedule(() -> startVotePhase(roomCode), gallerySeconds, TimeUnit.SECONDS);
        ScheduledFuture<?> prev = scheduledTasks.put(roomCode, f);
        if (prev != null) prev.cancel(false);
    }

    private static final int VOTE_SECONDS = 30;

    private void startVotePhase(String roomCode) {
        GameState gs = activeGames.get(roomCode);
        if (gs == null) return;

        synchronized (gs) {
            gs.setPhase(GameState.Phase.VOTING);
            gs.clearCanvasSnapshots();
            gs.clearVotes();
            realtimePublisher.publishGameEvent(roomCode, new GameEvent.VotePhase(VOTE_SECONDS));

            ScheduledFuture<?> f = scheduler.schedule(() -> resolveVoting(roomCode), VOTE_SECONDS, TimeUnit.SECONDS);
            ScheduledFuture<?> prev = scheduledTasks.put(roomCode, f);
            if (prev != null) prev.cancel(false);
        }
    }

    private void resolveVoting(String roomCode) {
        GameState gs = activeGames.get(roomCode);
        if (gs == null) return;

        synchronized (gs) {
            if (gs.getPhase() != GameState.Phase.VOTING) return;

            ScheduledFuture<?> scheduled = scheduledTasks.remove(roomCode);
            if (scheduled != null) scheduled.cancel(false);

            // Tally votes
            Map<Long, Long> tally = new HashMap<>();
            for (Long votedId : gs.getVotes().values()) {
                tally.merge(votedId, 1L, Long::sum);
            }

            // Find top candidate (no tie-break for now — tie = no elimination)
            Long eliminated = null;
            long maxVotes = 0;
            boolean tie = false;
            for (Map.Entry<Long, Long> entry : tally.entrySet()) {
                if (entry.getValue() > maxVotes) {
                    maxVotes = entry.getValue();
                    eliminated = entry.getKey();
                    tie = false;
                } else if (entry.getValue() == maxVotes) {
                    tie = true;
                }
            }
            if (tie) eliminated = null;

            boolean wasImpostor = eliminated != null && eliminated.equals(gs.getImpostorId());
            if (eliminated != null) gs.eliminatePlayer(eliminated);
            gs.clearVotes();

            Long finalEliminated = eliminated;
            realtimePublisher.publishGameEvent(roomCode,
                    new GameEvent.VoteResult(finalEliminated, wasImpostor, List.of()));

            if (wasImpostor) {
                realtimePublisher.publishGameEvent(roomCode,
                        new GameEvent.GameOver("PAINTERS", "VOTED_OUT", gs.getImpostorId(), gs.getSecretWord()));
                activeGames.remove(roomCode);
                return;
            }

            // Impostor wins if only 2 or fewer players remain
            if (gs.getAlivePlayers().size() <= 2) {
                realtimePublisher.publishGameEvent(roomCode,
                        new GameEvent.GameOver("IMPOSTOR", "LAST_STANDING", gs.getImpostorId(), gs.getSecretWord()));
                activeGames.remove(roomCode);
                return;
            }

            // Start new round after a short pause to let clients see the result
            int newRound = gs.getRound() + 1;
            gs.setRound(newRound);
            List<Long> aliveOrder = new ArrayList<>(gs.getDrawingOrder());
            aliveOrder.removeIf(id -> !gs.isPlayerAlive(id));
            gs.setDrawingOrder(aliveOrder);

            scheduler.schedule(() -> startNewRound(roomCode, newRound), 4, TimeUnit.SECONDS);
        }
    }

    private void startNewRound(String roomCode, int round) {
        GameState gs = activeGames.get(roomCode);
        if (gs == null) return;

        synchronized (gs) {
            realtimePublisher.publishGameEvent(roomCode,
                    new GameEvent.NewRound(round, gs.getDrawingOrder()));
            startNextTurn(roomCode);
        }
    }
}
