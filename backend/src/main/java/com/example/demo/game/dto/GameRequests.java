package com.example.demo.game.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class GameRequests {

    private GameRequests() {
    }

    public record CreateRoomRequest(
            @NotBlank @Size(min = 2, max = 32) String username,
            @Min(20) @Max(300) int roundDurationSeconds,
            @Min(10) @Max(120) int votingDurationSeconds,
            @Min(1) @Max(15) int maxRounds,
            @NotEmpty List<@NotBlank String> themes) {
    }

    public record JoinRoomRequest(
            @NotBlank @Size(min = 2, max = 32) String username) {
    }

    public record StartGameRequest(
            @Min(1) long playerId) {
    }

    public record StrokePoint(
            @Min(0) @Max(1000) int x,
            @Min(0) @Max(1000) int y) {
    }

    public record AddStrokeRequest(
            @Min(1) long playerId,
            @NotEmpty @Size(min = 2, max = 256) List<StrokePoint> points) {
    }

    public record VoteRequest(
            @Min(1) long voterPlayerId,
            @Min(1) long targetPlayerId) {
    }
}
