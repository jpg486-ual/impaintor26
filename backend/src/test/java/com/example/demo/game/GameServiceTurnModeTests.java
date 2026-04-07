package com.example.demo.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
    void turnMode_allowsVotingDuringDrawingAndResolvesWhenEveryoneVotes() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P2"));
        GameResponses.RoomJoinResponse p3 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P3"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        GameResponses.GameStateResponse stateBeforeVotes = gameService.getState(host.roomCode(), host.playerId());
        assertThat(stateBeforeVotes.gameMode()).isEqualTo(GameMode.TURN_BASED);
        assertThat(stateBeforeVotes.phase()).isEqualTo(GamePhase.DRAWING);

        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(host.playerId(), p2.playerId()));
        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(p2.playerId(), p2.playerId()));
        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(p3.playerId(), p2.playerId()));

        GameResponses.GameStateResponse afterVotes = gameService.getState(host.roomCode(), host.playerId());
        assertThat(afterVotes.phase()).isEqualTo(GamePhase.ROUND_RESULT);
        assertThat(afterVotes.resultMessage()).isNotBlank();
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
    void simultaneousMode_rejectsVotingDuringDrawing() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host3", 60, 20, 3, List.of("objetos"), GameMode.SIMULTANEOUS));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P32"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P33"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        assertThatThrownBy(() -> gameService.vote(host.roomCode(), new GameRequests.VoteRequest(host.playerId(), p2.playerId())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void vote_isIdempotent_doesNotThrowOnDuplicateVote() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host4", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        GameResponses.RoomJoinResponse p2 = gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P42"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P43"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        // Vote once
        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(host.playerId(), p2.playerId()));
        // Vote again — should NOT throw (idempotent)
        gameService.vote(host.roomCode(), new GameRequests.VoteRequest(host.playerId(), p2.playerId()));
    }

    @Test
    void turnMode_doesNotRotateDrawerWhenStrokeIsSubmitted() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host6", 60, 20, 3, List.of("animales"), GameMode.TURN_BASED));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P62"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P63"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));

        GameResponses.GameStateResponse beforeStroke = gameService.getState(host.roomCode(), host.playerId());
        long activeDrawer = beforeStroke.activeDrawerPlayerId();

        gameService.addStroke(host.roomCode(), new GameRequests.AddStrokeRequest(
                activeDrawer,
                List.of(new GameRequests.StrokePoint(10, 10), new GameRequests.StrokePoint(50, 50))));

        GameResponses.GameStateResponse afterStroke = gameService.getState(host.roomCode(), host.playerId());
        assertThat(afterStroke.activeDrawerPlayerId()).isEqualTo(activeDrawer);
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

        GameRoom room = roomRepository.findByCode(host.roomCode()).orElseThrow();
        room.setPhaseEndsAt(java.time.Instant.now().minusSeconds(1));
        roomRepository.save(room);

        boolean advanced = gameService.advanceRoomIfNeeded(host.roomCode());
        assertThat(advanced).isTrue();

        GameResponses.GameStateResponse afterTurnChange = gameService.getState(host.roomCode(), host.playerId());
        assertThat(afterTurnChange.activeDrawerPlayerId()).isNotEqualTo(currentDrawer);
        assertThat(afterTurnChange.strokes()).isEmpty();
        assertThat(afterTurnChange.phase()).isEqualTo(GamePhase.DRAWING);
    }

    @Test
    void startGame_isIdempotent_doesNotThrowIfAlreadyStarted() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("Host5", 60, 20, 3, List.of("comida"), GameMode.SIMULTANEOUS));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P52"));
        gameService.joinRoom(host.roomCode(), new GameRequests.JoinRoomRequest("P53"));

        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));
        // Start again — should NOT throw (idempotent)
        gameService.startGame(host.roomCode(), new GameRequests.StartGameRequest(host.playerId()));
    }
}
