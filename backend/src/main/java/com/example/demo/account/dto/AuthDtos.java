package com.example.demo.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 32) String username,
            @NotBlank @Email @Size(max = 128) String email,
            @NotBlank @Size(min = 6, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank String password) {
    }

    public record SessionResponse(
            long userId,
            String username,
            String email,
            int elo,
            int rankedGamesPlayed,
            int provisionalMatchesRemaining) {
    }
}
