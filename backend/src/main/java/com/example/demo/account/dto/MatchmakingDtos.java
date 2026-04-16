package com.example.demo.account.dto;

import java.time.Instant;

import com.example.demo.account.model.RankedQueueTicketStatus;
import com.example.demo.game.model.GameMode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public final class MatchmakingDtos {

    private MatchmakingDtos() {
    }

    public record JoinPublicQueueRequest(
            @Min(1) long userId,
            @NotNull GameMode gameMode) {
    }

    public record BootstrapPublicPlayerRequest(
            @NotBlank @Size(min = 2, max = 32) String username) {
    }

    public record LeavePublicQueueRequest(
            @Min(1) long userId) {
    }

    public record ConfirmPublicQueueMatchRequest(
            @Min(1) long userId) {
    }

    public record PublicQueueStatusResponse(
            boolean queued,
            Long ticketId,
            long userId,
            GameMode gameMode,
            RankedQueueTicketStatus status,
            int eloAtQueueTime,
            Integer searchMinElo,
            Integer searchMaxElo,
            long waitedSeconds,
            Instant queuedAt,
            String matchedRoomCode,
            Long matchedPlayerId,
            Long rankedMatchId,
            Instant confirmationDeadlineAt,
            boolean confirmed) {
    }

    public record PublicPlayerProfileResponse(
            long userId,
            String username,
            int elo,
            int rankedGamesPlayed,
            int provisionalMatchesRemaining) {
    }
}
