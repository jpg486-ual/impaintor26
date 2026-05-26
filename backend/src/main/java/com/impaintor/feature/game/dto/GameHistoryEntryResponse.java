package com.impaintor.feature.game.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameHistoryEntryResponse {
    private Long gameId;
    private LocalDateTime playedAt;
    private String role; // IMPOSTOR or PAINTER
    private boolean winner;
    private Integer eloChange;
    private String roomCode;
}
