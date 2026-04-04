package com.example.demo.game.dto;

import java.time.Instant;
import java.util.List;

import com.example.demo.game.model.GameMode;
import com.example.demo.game.model.GamePhase;

public final class GameResponses {

    private GameResponses() {
    }

    public record RoomJoinResponse(String roomCode, long playerId, boolean host) {
    }

    public record PlayerView(long id, String name, int score) {
    }

    public record StrokeView(long id, long playerId, List<GameRequests.StrokePoint> points) {
    }

    public record GameStateResponse(
            String roomCode,
            GameMode gameMode,
            GamePhase phase,
            int currentRound,
            int maxRounds,
            Instant phaseEndsAt,
            List<PlayerView> players,
            List<StrokeView> strokes,
            Long yourPlayerId,
            String yourWord,
            boolean youAreImpostor,
            boolean youAreHost,
            Long impostorRevealedPlayerId,
            Long activeDrawerPlayerId,
            Long majorityVotedPlayerId,
            Long yourVoteTargetPlayerId,
            String resultMessage,
            int totalVotesThisRound,
            int totalPlayers) {
    }
}
