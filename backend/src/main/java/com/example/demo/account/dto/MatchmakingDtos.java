package com.example.demo.account.dto;

import java.time.Instant;

import com.example.demo.account.model.RankedQueueTicketStatus;
import com.example.demo.game.model.GameMode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class MatchmakingDtos {

    private MatchmakingDtos() {
    }

    public record JoinPublicQueueRequest(
            @Min(1) long userId,
            @NotNull GameMode gameMode) {
    }

    public record LeavePublicQueueRequest(
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
            String matchedRoomCode) {
    }
}
