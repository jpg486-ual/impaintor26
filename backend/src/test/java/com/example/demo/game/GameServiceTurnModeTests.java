package com.example.demo.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.game.dto.GameRequests;
import com.example.demo.game.dto.GameResponses;
import com.example.demo.game.model.GameMode;
import com.example.demo.game.model.GamePhase;
import com.example.demo.game.model.GameRoom;
import com.example.demo.game.repository.GameRoomRepository;
import com.example.demo.game.service.GameService;

@SpringBootTest
class GameServiceTurnModeTests {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRoomRepository roomRepository;

    @Test
    void turnMode_allowsVotingDuringDrawing() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P2"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P3"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(host.playerId(), p2.playerId()));

        GameResponses.GameStateResponse state = gameService.getState(host.roomCode(), host.playerId());
        assertThat(state.phase()).isEqualTo(GamePhase.DRAWING);
        assertThat(state.totalVotesThisRound()).isEqualTo(1);
        assertThat(state.yourVoteTargetPlayerId()).isEqualTo(p2.playerId());
    }

    @Test
    void turnMode_turnsKeepRotatingWithoutEnteringVotingPhase() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostV", 45, 25, 3, List.of("animales"), GameMode.TURN_BASED));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P2V"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P3V"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        GameResponses.GameStateResponse firstState = gameService.getState(host.roomCode(), host.playerId());
        long firstDrawer = firstState.activeDrawerPlayerId();
        assertThat(firstState.phase()).isEqualTo(GamePhase.DRAWING);

        forcePhaseTimeout(host.roomCode());
        boolean advanced = gameService.advanceRoomIfNeeded(host.roomCode());
        assertThat(advanced).isTrue();

        GameResponses.GameStateResponse secondState = gameService.getState(host.roomCode(), host.playerId());
        long secondDrawer = secondState.activeDrawerPlayerId();
        assertThat(secondState.phase()).isEqualTo(GamePhase.DRAWING);
        assertThat(secondState.phaseEndsAt()).isNotNull();
        assertThat(secondDrawer).isNotEqualTo(firstDrawer);

        forcePhaseTimeout(host.roomCode());
        gameService.advanceRoomIfNeeded(host.roomCode());
        GameResponses.GameStateResponse thirdState = gameService.getState(host.roomCode(), host.playerId());
        long thirdDrawer = thirdState.activeDrawerPlayerId();
        assertThat(thirdState.phase()).isEqualTo(GamePhase.DRAWING);
        assertThat(thirdDrawer).isNotEqualTo(secondDrawer);

        forcePhaseTimeout(host.roomCode());
        gameService.advanceRoomIfNeeded(host.roomCode());
        GameResponses.GameStateResponse fourthState = gameService.getState(host.roomCode(), host.playerId());
        assertThat(fourthState.phase()).isEqualTo(GamePhase.DRAWING);
        assertThat(fourthState.activeDrawerPlayerId()).isEqualTo(firstDrawer);
    }

    @Test
    void turnMode_resolvesWhenEveryoneVotesDuringDrawing() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostResolve", 45, 25, 3, List.of("animales"), GameMode.TURN_BASED));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P2Resolve"));
        GameResponses.RoomJoinResponse p3 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P3Resolve"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(host.playerId(), p2.playerId()));
        GameResponses.GameStateResponse afterHostVote = gameService.getState(host.roomCode(), host.playerId());
        assertThat(afterHostVote.phase()).isEqualTo(GamePhase.DRAWING);

        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(p2.playerId(), p2.playerId()));
        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(p3.playerId(), p2.playerId()));

        GameResponses.GameStateResponse afterVotes = gameService.getState(host.roomCode(), host.playerId());
        assertThat(afterVotes.phase()).isEqualTo(GamePhase.ROUND_RESULT);
        assertThat(afterVotes.resultMessage()).isNotBlank();
    }

    @Test
    void turnMode_hostCanSkipVotingDuringDrawing() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostSkip", 45, 25, 3, List.of("comida"), GameMode.TURN_BASED));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P2S"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P3S"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        gameService.skipVoting(host.roomCode(), new GameRequests.SkipVotingRequest(host.playerId()));

        GameResponses.GameStateResponse afterSkip = gameService.getState(host.roomCode(), host.playerId());
        assertThat(afterSkip.phase()).isEqualTo(GamePhase.ROUND_RESULT);
    }

    @Test
    void turnMode_nonHostCannotSkipVotingDuringDrawing() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostSkip2", 45, 25, 3, List.of("objetos"), GameMode.TURN_BASED));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P2S2"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P3S2"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        assertThatThrownBy(() -> gameService.skipVoting(host.roomCode(), new GameRequests.SkipVotingRequest(p2.playerId())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void turnMode_onlyActiveDrawerCanDraw() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host2", 60, 20, 3, List.of("comida"), GameMode.TURN_BASED));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P22"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P23"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        GameResponses.GameStateResponse state = gameService.getState(host.roomCode(), host.playerId());
        assertThat(state.activeDrawerPlayerId()).isNotNull();

        long nonActive = state.players().stream()
                .map(GameResponses.PlayerView::id)
                .filter(id -> id != state.activeDrawerPlayerId())
                .findFirst()
                .orElse(p2.playerId());

        GameRequests.AddStrokeRequest stroke = new GameRequests.AddStrokeRequest(
                nonActive,
                List.of(new GameRequests.StrokePoint(10, 10), new GameRequests.StrokePoint(50, 50)));

        assertThatThrownBy(() -> gameService.addStroke(host.roomCode(), stroke))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void turnMode_rotatesDrawerOnTimeoutAndClearsRoundStrokes() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host7", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P72"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P73"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        GameResponses.GameStateResponse beforeTurnChange = gameService.getState(host.roomCode(), host.playerId());
        long currentDrawer = beforeTurnChange.activeDrawerPlayerId();

        gameService.addStroke(host.roomCode(), new GameRequests.AddStrokeRequest(
                currentDrawer,
                List.of(new GameRequests.StrokePoint(20, 20), new GameRequests.StrokePoint(80, 80))));

        forcePhaseTimeout(host.roomCode());

        boolean advanced = gameService.advanceRoomIfNeeded(host.roomCode());
        assertThat(advanced).isTrue();

        GameResponses.GameStateResponse afterTurnChange = gameService.getState(host.roomCode(), host.playerId());
        assertThat(afterTurnChange.activeDrawerPlayerId()).isNotEqualTo(currentDrawer);
        assertThat(afterTurnChange.strokes()).isEmpty();
        assertThat(afterTurnChange.phase()).isEqualTo(GamePhase.DRAWING);
    }

    @Test
    void turnMode_observersSeeSharedTurnCanvasStrokes() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostCanvas", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P2Canvas"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P3Canvas"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        GameResponses.GameStateResponse state = gameService.getState(host.roomCode(), host.playerId());
        long activeDrawer = state.activeDrawerPlayerId();

        gameService.addStroke(host.roomCode(), new GameRequests.AddStrokeRequest(
                activeDrawer,
                List.of(new GameRequests.StrokePoint(40, 40), new GameRequests.StrokePoint(120, 120))));

        long observerId = p2.playerId() == activeDrawer ? host.playerId() : p2.playerId();
        GameResponses.GameStateResponse observerState = gameService.getState(host.roomCode(), observerId);

        assertThat(observerState.strokes()).hasSize(1);
        assertThat(observerState.strokes().get(0).playerId()).isEqualTo(activeDrawer);
    }

    @Test
    void vote_isIdempotent_doesNotThrowOnDuplicateVote() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host4", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P42"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P43"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(host.playerId(), p2.playerId()));
        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(host.playerId(), p2.playerId()));

        GameResponses.GameStateResponse state = gameService.getState(host.roomCode(), host.playerId());
        assertThat(state.totalVotesThisRound()).isEqualTo(1);
    }

    @Test
    void startGame_isIdempotent_doesNotThrowIfAlreadyStarted() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host5", 60, 20, 3, List.of("comida"), GameMode.SIMULTANEOUS));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P52"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P53"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));
        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));
    }

    @Test
    void cleanup_keepsRecentlyFinishedTurnRoom() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostKeep", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        GameRoom room = roomRepository.findByCode(host.roomCode()).orElseThrow();
        room.setPhase(GamePhase.FINISHED);
        room.setFinishedAt(Instant.now().minusSeconds(5));
        roomRepository.save(room);

        gameService.cleanupFinishedRooms();

        assertThat(roomRepository.findByCode(host.roomCode())).isPresent();
    }

    @Test
    void cleanup_removesOldFinishedTurnRoom() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostDrop", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        GameRoom room = roomRepository.findByCode(host.roomCode()).orElseThrow();
        room.setPhase(GamePhase.FINISHED);
        room.setFinishedAt(Instant.now().minusSeconds(90));
        roomRepository.save(room);

        gameService.cleanupFinishedRooms();

        assertThat(roomRepository.findByCode(host.roomCode())).isEmpty();
    }

    @Test
    void cleanup_removesStaleTurnRoomWithoutFinishedState() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostStale", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        GameRoom room = roomRepository.findByCode(host.roomCode()).orElseThrow();
        room.setPhase(GamePhase.DRAWING);
        ReflectionTestUtils.setField(room, "createdAt", Instant.now().minusSeconds(3700));
        roomRepository.save(room);

        gameService.cleanupFinishedRooms();

        assertThat(roomRepository.findByCode(host.roomCode())).isEmpty();
    }

    private void forcePhaseTimeout(String roomCode) {
        GameRoom room = roomRepository.findByCode(roomCode).orElseThrow();
        room.setPhaseEndsAt(Instant.now().minusSeconds(1));
        roomRepository.save(room);
    }
}
