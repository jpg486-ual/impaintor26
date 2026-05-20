package com.impaintor.feature.game.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.impaintor.feature.game.model.GameState;
import com.impaintor.feature.realtime.dto.outbound.RoleAssignment;
import com.impaintor.feature.realtime.service.RealtimePublisher;
import com.impaintor.feature.room.models.Room;
import com.impaintor.feature.room.repository.RoomRepository;
import com.impaintor.feature.user.models.User;
import com.impaintor.feature.wordgroup.models.WordGroup;
import com.impaintor.feature.wordgroup.repositories.WordGroupRepository;

/**
 * Inicializa y conserva el estado en memoria de una partida por sala.
 */
@Service
public class GameService {

    private final RoomRepository roomRepository;
    private final WordGroupRepository wordGroupRepository;
    private final RealtimePublisher realtimePublisher;

    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    public GameService(RoomRepository roomRepository,
                       WordGroupRepository wordGroupRepository,
                       RealtimePublisher realtimePublisher) {
        this.roomRepository = roomRepository;
        this.wordGroupRepository = wordGroupRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public GameState initializeGame(String roomCode) {
        GameState existing = activeGames.get(roomCode);
        if (existing != null) {
            return existing;
        }

        Room room = roomRepository.findById(roomCode)
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
        gameState.setImpostorLives(resolveImpostorLives(room));

        activeGames.put(roomCode, gameState);

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

    private int resolveImpostorLives(Room room) {
        return room.getImpostorTries() != null ? room.getImpostorTries() : 1;
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
                        new RoleAssignment.Impostor(gameState.getHintWord(), gameState.getImpostorLives()));
            } else {
                realtimePublisher.sendRoleAssignment(playerId,
                        new RoleAssignment.Painter(gameState.getSecretWord()));
            }
        }
    }
}
