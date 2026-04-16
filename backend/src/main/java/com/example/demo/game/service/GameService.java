package com.example.demo.game.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.account.model.RankedMatch;
import com.example.demo.account.model.RankedMatchStatus;
import com.example.demo.account.model.UserRankProfile;
import com.example.demo.account.repository.RankedMatchRepository;
import com.example.demo.account.service.RatingService;
import com.example.demo.game.dto.GameRequests;
import com.example.demo.game.dto.GameResponses;
import com.example.demo.game.model.DrawingStroke;
import com.example.demo.game.model.GameMode;
import com.example.demo.game.model.GamePhase;
import com.example.demo.game.model.GamePlayer;
import com.example.demo.game.model.GameRoom;
import com.example.demo.game.model.GameRoomType;
import com.example.demo.game.model.GameVote;
import com.example.demo.game.repository.DrawingStrokeRepository;
import com.example.demo.game.repository.GamePlayerRepository;
import com.example.demo.game.repository.GameRoomRepository;
import com.example.demo.game.repository.GameVoteRepository;

@Service
public class GameService {

    private static final int MAX_PLAYERS = 6;
    private static final int MIN_PLAYERS_TO_START = 3;
    private static final int IMPOSTOR_WIN_POINTS = 3;
    private static final int CREW_WIN_POINTS = 1;
    private static final int RESULT_SECONDS = 8;
    private static final int ROOM_TTL_AFTER_FINISH_SECONDS = 60;
    private static final int ROOM_TTL_SECONDS = 3600; // Tiempo de vida de una sala desde su creación, para evitar que salas en modo por turnos queden activas indefinidamente
    private static final int RANKED_ROUND_DURATION_SECONDS = 60;
    private static final int RANKED_VOTING_DURATION_SECONDS = 25;
    private static final int RANKED_MAX_ROUNDS = 5;
    private static final List<String> RANKED_THEMES = List.of("animales", "comida", "deportes", "objetos", "profesiones");
    private static final String HINT_SEPARATOR = "\n";

    private static final Map<String, List<String>> WORDS_BY_THEME = Map.of(
            "animales",
            List.of("gato", "perro", "elefante", "jirafa", "delfin", "lobo", "tigre", "serpiente", "aguila",
                    "tortuga", "rana", "caballo", "ballena", "pinguino", "leon", "mariposa", "pulpo", "conejo"),
            "comida",
            List.of("pizza", "hamburguesa", "sushi", "ensalada", "helado", "taco", "pasta", "donut", "sandwich",
                    "croissant", "paella", "chocolate", "galleta", "burrito", "tortilla", "crepé", "sopa"),
            "deportes",
            List.of("futbol", "tenis", "baloncesto", "natacion", "ciclismo", "boxeo", "surf", "esqui", "golf",
                    "voleibol", "beisbol", "patinaje", "rugby", "esgrima", "kayak"),
            "objetos",
            List.of("reloj", "lampara", "mochila", "tijera", "paraguas", "guitarra", "telefono", "camara",
                    "teclado", "silla", "espejo", "libro", "vela", "candado", "globo", "cuchillo", "llave"),
            "profesiones",
            List.of("medico", "bombero", "profesor", "arquitecto", "piloto", "chef", "astronauta", "detective",
                    "musico", "pintor", "fotografo", "jardinero", "carpintero", "mago", "pirata"));

    private final GameRoomRepository roomRepository;
    private final GamePlayerRepository playerRepository;
    private final DrawingStrokeRepository strokeRepository;
    private final GameVoteRepository voteRepository;
    private final RankedMatchRepository rankedMatchRepository;
    private final RatingService ratingService;
    private final ImpostorHintService impostorHintService;
    private final SimpMessagingTemplate messaging;
    private final SecureRandom random = new SecureRandom();

    public record RankedPlayerSeed(long userId, String username) {
    }

    public GameService(
            GameRoomRepository roomRepository,
            GamePlayerRepository playerRepository,
            DrawingStrokeRepository strokeRepository,
            GameVoteRepository voteRepository,
            RankedMatchRepository rankedMatchRepository,
            RatingService ratingService,
            ImpostorHintService impostorHintService,
            SimpMessagingTemplate messaging) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.strokeRepository = strokeRepository;
        this.voteRepository = voteRepository;
        this.rankedMatchRepository = rankedMatchRepository;
        this.ratingService = ratingService;
        this.impostorHintService = impostorHintService;
        this.messaging = messaging;
    }

    @Transactional
    public GameResponses.RoomJoinResponse createRoom(GameRequests.CreateRoomRequest request) {
        String normalizedName = normalizeName(request.username());
        List<String> themes = normalizeThemes(request.themes());

        GameRoom room = new GameRoom();
        room.setCode(generateCode());
        room.setHostName(normalizedName);
        room.setRoundDurationSeconds(request.roundDurationSeconds());
        room.setVotingDurationSeconds(request.votingDurationSeconds());
        room.setMaxRounds(request.maxRounds());
        room.setThemesCsv(String.join(",", themes));
        room.setCurrentRound(0);
        room.setGameMode(request.gameMode() == null ? GameMode.SIMULTANEOUS : request.gameMode());
        room.setRoomType(GameRoomType.PRIVATE_UNRANKED);
        room.setRankedMatchId(null);
        room.setPhase(GamePhase.WAITING);
        room.setActiveDrawerTurnIndex(0);
        room.setTurnsCompletedInRound(0);

        roomRepository.save(room);

        GamePlayer player = new GamePlayer();
        player.setRoomCode(room.getCode());
        player.setName(normalizedName);
        player.setScore(0);
        player.setUserId(null);
        playerRepository.save(player);

        return new GameResponses.RoomJoinResponse(room.getCode(), player.getId(), true);
    }

    @Transactional
    public String createRankedRoom(long rankedMatchId, GameMode gameMode, List<RankedPlayerSeed> rankedPlayers) {
        if (gameMode != GameMode.TURN_BASED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Las partidas ranked solo permiten modo por turnos");
        }
        if (rankedPlayers == null || rankedPlayers.size() < MIN_PLAYERS_TO_START) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Se requieren al menos 3 jugadores para ranked");
        }
        if (rankedPlayers.size() > MAX_PLAYERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La sala ranked no puede exceder 6 jugadores");
        }

        List<RankedPlayerSeed> normalizedPlayers = rankedPlayers.stream()
                .map(player -> new RankedPlayerSeed(player.userId(), normalizeName(player.username())))
                .toList();
        String hostName = normalizedPlayers.get(0).username();

        GameRoom room = new GameRoom();
        room.setCode(generateCode());
        room.setHostName(hostName);
        room.setRoundDurationSeconds(RANKED_ROUND_DURATION_SECONDS);
        room.setVotingDurationSeconds(RANKED_VOTING_DURATION_SECONDS);
        room.setMaxRounds(RANKED_MAX_ROUNDS);
        room.setThemesCsv(String.join(",", RANKED_THEMES));
        room.setCurrentRound(0);
        room.setGameMode(GameMode.TURN_BASED);
        room.setRoomType(GameRoomType.PUBLIC_RANKED);
        room.setRankedMatchId(rankedMatchId);
        room.setPhase(GamePhase.WAITING);
        room.setActiveDrawerTurnIndex(0);
        room.setTurnsCompletedInRound(0);
        room = roomRepository.save(room);

        for (RankedPlayerSeed rankedPlayer : normalizedPlayers) {
            GamePlayer player = new GamePlayer();
            player.setRoomCode(room.getCode());
            player.setName(rankedPlayer.username());
            player.setScore(0);
            player.setUserId(rankedPlayer.userId());
            playerRepository.save(player);
        }

        beginRound(room);
        broadcastState(room.getCode());
        return room.getCode();
    }

    @Transactional
    public GameResponses.RoomJoinResponse joinRoom(String roomCode, GameRequests.JoinRoomRequest request) {
        GameRoom room = getRoomOrThrow(roomCode);
        validateRoomActive(room);
        if (room.getPhase() != GamePhase.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La partida ya ha comenzado");
        }

        if (playerRepository.countByRoomCode(room.getCode()) >= MAX_PLAYERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La sala ya tiene 6 jugadores");
        }

        String normalizedName = normalizeName(request.username());
        if (playerRepository.existsByRoomCodeAndNameIgnoreCase(room.getCode(), normalizedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nombre de usuario ya usado en la sala");
        }

        GamePlayer player = new GamePlayer();
        player.setRoomCode(room.getCode());
        player.setName(normalizedName);
        player.setScore(0);
        player.setUserId(null);
        playerRepository.save(player);

        broadcastState(room.getCode());

        return new GameResponses.RoomJoinResponse(room.getCode(), player.getId(),
                normalizedName.equals(room.getHostName()));
    }

    @Transactional
    public void startGame(String roomCode, GameRequests.StartGameRequest request) {
        GameRoom room = getRoomOrThrow(roomCode);
        validateRoomActive(room);

        // Idempotent: if game already started, just return
        if (room.getPhase() != GamePhase.WAITING) {
            return;
        }

        GamePlayer host = playerRepository.findByIdAndRoomCode(request.playerId(), room.getCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Jugador inválido"));

        if (!host.getName().equals(room.getHostName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el creador puede iniciar");
        }

        if (playerRepository.countByRoomCode(room.getCode()) < MIN_PLAYERS_TO_START) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Se requieren al menos 3 jugadores");
        }

        beginRound(room);
        broadcastState(room.getCode());
    }

    @Transactional
    public void addStroke(String roomCode, GameRequests.AddStrokeRequest request) {
        GameRoom room = getRoomOrThrow(roomCode);

        if (room.getPhase() != GamePhase.DRAWING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede dibujar fuera de la fase de dibujo");
        }

        GamePlayer drawer = playerRepository.findByIdAndRoomCode(request.playerId(), roomCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Jugador inválido"));

        if (room.getGameMode() == GameMode.TURN_BASED
                && !drawer.getId().equals(room.getActiveDrawerPlayerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No es tu turno de dibujo");
        }

        DrawingStroke stroke = new DrawingStroke();
        stroke.setRoomCode(roomCode);
        stroke.setRoundNumber(room.getCurrentRound());
        stroke.setPlayerId(request.playerId());
        stroke.setPathData(encodePoints(request.points()));
        strokeRepository.save(stroke);

        // Broadcast the new stroke immediately
        broadcastState(room.getCode());
    }

    @Transactional
    public void vote(String roomCode, GameRequests.VoteRequest request) {
        GameRoom room = getRoomOrThrow(roomCode);

        boolean validVotingPhase = room.getPhase() == GamePhase.VOTING
                || (room.getGameMode() == GameMode.TURN_BASED && room.getPhase() == GamePhase.DRAWING);

        if (!validVotingPhase) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ahora no es fase de voto");
        }

        playerRepository.findByIdAndRoomCode(request.voterPlayerId(), roomCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Votante inválido"));

        playerRepository.findByIdAndRoomCode(request.targetPlayerId(), roomCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Objetivo inválido"));

        // Idempotent: if already voted, silently return
        if (voteRepository.findByRoomCodeAndRoundNumberAndVoterPlayerId(roomCode, room.getCurrentRound(),
                request.voterPlayerId()).isPresent()) {
            return;
        }

        GameVote vote = new GameVote();
        vote.setRoomCode(roomCode);
        vote.setRoundNumber(room.getCurrentRound());
        vote.setVoterPlayerId(request.voterPlayerId());
        vote.setTargetPlayerId(request.targetPlayerId());
        voteRepository.save(vote);

        int players = playerRepository.countByRoomCode(roomCode);
        int votes = voteRepository.countByRoomCodeAndRoundNumber(roomCode, room.getCurrentRound());
        if (votes >= players) {
            resolveVotes(room);
        }

        broadcastState(room.getCode());
    }

    @Transactional
    public void skipVoting(String roomCode, GameRequests.SkipVotingRequest request) {
        GameRoom room = getRoomOrThrow(roomCode);
        if (room.getGameMode() != GameMode.TURN_BASED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo disponible en modo por turnos");
        }

        GamePlayer host = playerRepository.findByIdAndRoomCode(request.playerId(), roomCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Jugador inválido"));
        if (!host.getName().equals(room.getHostName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el host puede omitir la votación");
        }

        // Idempotent: if voting is already closed, silently return.
        if (room.getPhase() != GamePhase.DRAWING && room.getPhase() != GamePhase.VOTING) {
            return;
        }

        resolveVotes(room);
        broadcastState(room.getCode());
    }

    @Transactional(readOnly = true)
    public GameResponses.GameStateResponse getState(String roomCode, long playerId) {
        GameRoom room = getRoomOrThrow(roomCode);

        GamePlayer viewer = playerRepository.findByIdAndRoomCode(playerId, roomCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Jugador inválido"));

        return buildStateResponse(room, viewer);
    }

    @Transactional
    public boolean advanceRoomIfNeeded(String roomCode) {
        GameRoom room = roomRepository.findByCode(roomCode.toUpperCase(Locale.ROOT)).orElse(null);
        if (room == null) {
            return false;
        }
        return advanceRoomState(room);
    }

    @Transactional
    public void cleanupFinishedRooms() {
        Instant now = Instant.now();
        List<GameRoom> finishedRooms = roomRepository.findAll().stream()
                .filter(room -> shouldDeleteRoom(room, now))
                .toList();

        for (GameRoom room : finishedRooms) {
            String code = room.getCode();
            voteRepository.deleteByRoomCode(code);
            strokeRepository.deleteByRoomCode(code);
            playerRepository.deleteByRoomCode(code);
            roomRepository.delete(room);
        }
    }

    private boolean shouldDeleteRoom(GameRoom room, Instant now) {
        if (room.getPhase() == GamePhase.FINISHED && room.getFinishedAt() != null) {
            return room.getFinishedAt().isBefore(now.minusSeconds(ROOM_TTL_AFTER_FINISH_SECONDS));
        }

        // Turn mode can remain in DRAWING while waiting for votes; clear stale rooms after hard TTL.
        return room.getGameMode() == GameMode.TURN_BASED
                && room.getCreatedAt() != null
                && room.getCreatedAt().isBefore(now.minusSeconds(ROOM_TTL_SECONDS));
    }

    public void broadcastState(String roomCode) {
        try {
            GameRoom room = roomRepository.findByCode(roomCode.toUpperCase(Locale.ROOT)).orElse(null);
            if (room == null)
                return;

            List<GamePlayer> players = playerRepository.findByRoomCodeOrderByJoinedAtAsc(roomCode);
            List<DrawingStroke> strokes = room.getCurrentRound() > 0
                    ? strokeRepository.findByRoomCodeAndRoundNumberOrderByIdAsc(roomCode, room.getCurrentRound())
                    : List.of();
            List<GameVote> votes = room.getCurrentRound() > 0
                    ? voteRepository.findByRoomCodeAndRoundNumber(roomCode, room.getCurrentRound())
                    : List.of();

            // Send personalized state to each player
            for (GamePlayer player : players) {
                GameResponses.GameStateResponse state = buildStateForPlayer(room, player, players, strokes, votes);
                messaging.convertAndSend(playerTopicDestination(roomCode, player.getId()), state);
            }
        } catch (Exception ignored) {
            // Broadcasting errors should not affect game logic
        }
    }

    private String playerTopicDestination(String roomCode, long playerId) {
        return "/topic/room." + roomCode + ".player." + playerId;
    }

    private GameResponses.GameStateResponse buildStateResponse(GameRoom room, GamePlayer viewer) {
        List<GamePlayer> players = playerRepository.findByRoomCodeOrderByJoinedAtAsc(room.getCode());
        List<DrawingStroke> strokes = room.getCurrentRound() > 0
                ? strokeRepository.findByRoomCodeAndRoundNumberOrderByIdAsc(room.getCode(), room.getCurrentRound())
                : List.of();
        List<GameVote> votes = room.getCurrentRound() > 0
                ? voteRepository.findByRoomCodeAndRoundNumber(room.getCode(), room.getCurrentRound())
                : List.of();

        return buildStateForPlayer(room, viewer, players, strokes, votes);
    }

    private GameResponses.GameStateResponse buildStateForPlayer(GameRoom room, GamePlayer viewer,
            List<GamePlayer> players, List<DrawingStroke> strokes, List<GameVote> votes) {

        Long votedTarget = votes.stream()
                .filter(v -> Objects.equals(v.getVoterPlayerId(), viewer.getId()))
                .map(GameVote::getTargetPlayerId)
                .findFirst()
                .orElse(null);

        Long majority = null;
        String resultMessage = null;
        Long impostorReveal = null;

        if (room.getPhase() == GamePhase.ROUND_RESULT || room.getPhase() == GamePhase.FINISHED) {
            majority = findMajorityVotedPlayer(votes);
            impostorReveal = room.getImpostorPlayerId();
            boolean crewWins = majority != null && majority.equals(room.getImpostorPlayerId());
            resultMessage = crewWins ? "¡Los pintores acertaron al impostor!"
                    : "¡El impostor engañó al grupo!";
        }

        String yourWord = null;
        List<String> impostorHints = List.of();
        boolean isImpostor = viewer.getId().equals(room.getImpostorPlayerId());
        if (room.getPhase() == GamePhase.DRAWING || room.getPhase() == GamePhase.VOTING) {
            if (!isImpostor) {
                yourWord = room.getCurrentWord();
            } else {
                impostorHints = decodeHints(room.getImpostorHintsCsv());
            }
        }

        int totalVotes = (int) votes.stream().map(GameVote::getVoterPlayerId).distinct().count();

        return new GameResponses.GameStateResponse(
                room.getCode(),
                room.getGameMode(),
                room.getPhase(),
                room.getCurrentRound(),
                room.getMaxRounds(),
                room.getPhaseEndsAt(),
                players.stream()
                        .map(p -> new GameResponses.PlayerView(p.getId(), p.getName(), p.getScore()))
                        .toList(),
                strokes.stream()
                        .map(s -> new GameResponses.StrokeView(s.getId(), s.getPlayerId(),
                                decodePoints(s.getPathData())))
                        .toList(),
                viewer.getId(),
                yourWord,
                impostorHints,
                isImpostor,
                viewer.getName().equals(room.getHostName()),
                impostorReveal,
                room.getActiveDrawerPlayerId(),
                majority,
                votedTarget,
                resultMessage,
                totalVotes,
                players.size());
    }

    private boolean advanceRoomState(GameRoom room) {
        if (room.getPhaseEndsAt() == null) {
            return false;
        }

        Instant now = Instant.now();
        if (room.getPhaseEndsAt().isAfter(now)) {
            return false;
        }

        if (room.getPhase() == GamePhase.DRAWING) {
            if (room.getGameMode() == GameMode.SIMULTANEOUS) {
                beginVotingPhase(room, now);
            } else {
                advanceTurn(room, now);
            }
            return true;
        }

        if (room.getPhase() == GamePhase.VOTING) {
            resolveVotes(room);
            return true;
        }

        if (room.getPhase() == GamePhase.ROUND_RESULT) {
            if (room.getCurrentRound() >= room.getMaxRounds()) {
                room.setPhase(GamePhase.FINISHED);
                room.setFinishedAt(now);
                room.setPhaseEndsAt(now.plusSeconds(ROOM_TTL_AFTER_FINISH_SECONDS));
                roomRepository.save(room);
                finalizeRankedMatchIfNeeded(room, now);
            } else {
                beginRound(room);
            }
            return true;
        }

        return false;
    }

    private void beginRound(GameRoom room) {
        int nextRound = room.getCurrentRound() + 1;
        room.setCurrentRound(nextRound);

        List<GamePlayer> players = playerRepository.findByRoomCodeOrderByJoinedAtAsc(room.getCode());
        GamePlayer impostor = players.get(random.nextInt(players.size()));
        room.setImpostorPlayerId(impostor.getId());
        room.setCurrentWord(pickWord(room.getThemesCsv()));
        room.setImpostorHintsCsv(encodeHints(impostorHintService.generateHints(room.getCurrentWord())));
        room.setTurnsCompletedInRound(0);

        if (room.getGameMode() == GameMode.TURN_BASED) {
            startTurn(room, players, 0, Instant.now());
        } else {
            room.setPhase(GamePhase.DRAWING);
            room.setPhaseEndsAt(Instant.now().plusSeconds(room.getRoundDurationSeconds()));
            room.setActiveDrawerPlayerId(null);
            room.setActiveDrawerTurnIndex(0);
        }

        roomRepository.save(room);
    }

    private void advanceTurn(GameRoom room, Instant now) {
        List<GamePlayer> players = playerRepository.findByRoomCodeOrderByJoinedAtAsc(room.getCode());
        if (players.isEmpty()) {
            return;
        }

        int nextTurnNumber = room.getTurnsCompletedInRound() + 1;
        startTurn(room, players, nextTurnNumber, now);
        roomRepository.save(room);
    }

    private void startTurn(GameRoom room, List<GamePlayer> players, int turnNumber, Instant now) {
        List<GamePlayer> turnOrder = buildTurnOrder(room, players);
        if (turnOrder.isEmpty()) {
            return;
        }
        int turnOrderIndex = Math.floorMod(turnNumber, turnOrder.size());

        // In turn mode, each player starts with a clean board when their turn begins.
        strokeRepository.deleteByRoomCodeAndRoundNumber(room.getCode(), room.getCurrentRound());

        room.setPhase(GamePhase.DRAWING);
        room.setTurnsCompletedInRound(turnNumber);
        room.setActiveDrawerTurnIndex(turnOrderIndex);
        room.setActiveDrawerPlayerId(turnOrder.get(turnOrderIndex).getId());
        room.setPhaseEndsAt(now.plusSeconds(room.getRoundDurationSeconds()));
    }

    private String encodeHints(List<String> hints) {
        if (hints == null || hints.isEmpty()) {
            return null;
        }
        return hints.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(3)
                .collect(Collectors.joining(HINT_SEPARATOR));
    }

    private List<String> decodeHints(String rawHints) {
        if (rawHints == null || rawHints.isBlank()) {
            return List.of();
        }
        return List.of(rawHints.split(HINT_SEPARATOR)).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(3)
                .toList();
    }

    private void beginVotingPhase(GameRoom room, Instant now) {
        room.setPhase(GamePhase.VOTING);
        room.setActiveDrawerPlayerId(null);
        room.setPhaseEndsAt(room.getGameMode() == GameMode.TURN_BASED
                ? null
                : now.plusSeconds(room.getVotingDurationSeconds()));
        roomRepository.save(room);
    }

    private List<GamePlayer> buildTurnOrder(GameRoom room, List<GamePlayer> players) {
        List<GamePlayer> shuffled = new ArrayList<>(players);
        long seed = Objects.hash(room.getCode(), room.getCurrentRound());
        Collections.shuffle(shuffled, new Random(seed));
        return shuffled;
    }

    private void resolveVotes(GameRoom room) {
        List<GameVote> votes = voteRepository.findByRoomCodeAndRoundNumber(room.getCode(), room.getCurrentRound());
        Long majority = findMajorityVotedPlayer(votes);
        boolean crewWins = majority != null && majority.equals(room.getImpostorPlayerId());

        List<GamePlayer> players = playerRepository.findByRoomCodeOrderByJoinedAtAsc(room.getCode());
        for (GamePlayer player : players) {
            if (crewWins) {
                if (!player.getId().equals(room.getImpostorPlayerId())) {
                    player.setScore(player.getScore() + CREW_WIN_POINTS);
                }
            } else if (player.getId().equals(room.getImpostorPlayerId())) {
                player.setScore(player.getScore() + IMPOSTOR_WIN_POINTS);
            }
        }
        playerRepository.saveAll(players);

        room.setPhase(GamePhase.ROUND_RESULT);
        room.setActiveDrawerPlayerId(null);
        room.setPhaseEndsAt(Instant.now().plusSeconds(RESULT_SECONDS));
        roomRepository.save(room);
    }

    private void finalizeRankedMatchIfNeeded(GameRoom room, Instant now) {
        if (room.getRoomType() != GameRoomType.PUBLIC_RANKED || room.getRankedMatchId() == null) {
            return;
        }

        RankedMatch rankedMatch = rankedMatchRepository.findById(room.getRankedMatchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Partida ranked inconsistente"));

        if (rankedMatch.getStatus() == RankedMatchStatus.FINISHED) {
            return;
        }

        List<GamePlayer> rankedPlayers = playerRepository.findByRoomCodeOrderByJoinedAtAsc(room.getCode()).stream()
                .filter(player -> player.getUserId() != null)
                .toList();

        if (rankedPlayers.size() < 2) {
            rankedMatch.setStatus(RankedMatchStatus.CANCELLED);
            rankedMatch.setFinishedAt(now);
            rankedMatchRepository.save(rankedMatch);
            return;
        }

        int highestScore = rankedPlayers.stream().mapToInt(GamePlayer::getScore).max().orElse(0);
        Set<Long> winners = rankedPlayers.stream()
                .filter(player -> player.getScore() == highestScore)
                .map(GamePlayer::getId)
                .collect(Collectors.toSet());

        Map<Long, UserRankProfile> profiles = new HashMap<>();
        for (GamePlayer player : rankedPlayers) {
            profiles.put(player.getId(), ratingService.getOrCreateProfile(player.getUserId()));
        }

        for (GamePlayer player : rankedPlayers) {
            UserRankProfile profile = profiles.get(player.getId());
            double opponentAverageElo = rankedPlayers.stream()
                    .filter(other -> !other.getId().equals(player.getId()))
                    .map(other -> profiles.get(other.getId()).getElo())
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(profile.getElo());

            double actualScore = winners.contains(player.getId()) ? 1d : 0d;
            int kFactor = ratingService.resolveKFactor(profile);
            double expectedScore = ratingService.expectedScore(profile.getElo(), opponentAverageElo);
            int nextElo = ratingService.computeNextElo(profile.getElo(), expectedScore, actualScore, kFactor);
            ratingService.recordRatingUpdate(rankedMatch.getId(), player.getUserId(), nextElo, kFactor);
        }

        rankedMatch.setStatus(RankedMatchStatus.FINISHED);
        rankedMatch.setFinishedAt(now);
        rankedMatchRepository.save(rankedMatch);
    }

    private Long findMajorityVotedPlayer(List<GameVote> votes) {
        Map<Long, Integer> tally = new HashMap<>();
        for (GameVote vote : votes) {
            tally.merge(vote.getTargetPlayerId(), 1, Integer::sum);
        }

        int max = tally.values().stream().max(Integer::compareTo).orElse(0);
        if (max == 0) {
            return null;
        }

        List<Long> candidates = tally.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();

        if (candidates.size() != 1) {
            return null;
        }

        return candidates.get(0);
    }

    private String generateCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 100; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < 6; j++) {
                code.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String value = code.toString();
            if (roomRepository.findByCode(value).isEmpty()) {
                return value;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar código de sala");
    }

    private GameRoom getRoomOrThrow(String roomCode) {
        return roomRepository.findByCode(roomCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala no encontrada"));
    }

    private void validateRoomActive(GameRoom room) {
        if (room.getPhase() == GamePhase.FINISHED && room.getFinishedAt() != null
                && room.getFinishedAt().isBefore(Instant.now().minusSeconds(ROOM_TTL_AFTER_FINISH_SECONDS))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala finalizada");
        }
    }

    private String normalizeName(String username) {
        String trimmed = username == null ? "" : username.trim();
        if (trimmed.length() < 2 || trimmed.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre debe tener entre 2 y 32 caracteres");
        }
        return trimmed;
    }

    private List<String> normalizeThemes(List<String> requestedThemes) {
        if (requestedThemes == null || requestedThemes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos un tema");
        }

        Set<String> allowed = WORDS_BY_THEME.keySet();
        List<String> normalized = requestedThemes.stream()
                .map(theme -> theme == null ? "" : theme.trim().toLowerCase(Locale.ROOT))
                .filter(allowed::contains)
                .distinct()
                .toList();

        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Temas inválidos");
        }

        return normalized;
    }

    private String pickWord(String themesCsv) {
        List<String> themes = List.of(themesCsv.split(","));
        String selectedTheme = themes.get(random.nextInt(themes.size()));
        List<String> words = Optional.ofNullable(WORDS_BY_THEME.get(selectedTheme))
                .filter(list -> !list.isEmpty())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Tema sin palabras"));
        return words.get(random.nextInt(words.size()));
    }

    private String encodePoints(List<GameRequests.StrokePoint> points) {
        return points.stream().map(p -> p.x() + ":" + p.y()).collect(Collectors.joining(";"));
    }

    private List<GameRequests.StrokePoint> decodePoints(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        List<GameRequests.StrokePoint> points = new ArrayList<>();
        for (String pair : encoded.split(";")) {
            String[] coords = pair.split(":");
            if (coords.length != 2) {
                continue;
            }
            points.add(new GameRequests.StrokePoint(Integer.parseInt(coords[0]), Integer.parseInt(coords[1])));
        }
        return points;
    }
}
