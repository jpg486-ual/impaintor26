package com.impaintor.feature.game.dto;

import java.time.LocalDateTime;
import java.util.List;

public class GameDetailResponse {

    private Long gameId;
    private String roomCode;
    private String mode;
    private String secretWord;
    private String winningSide;
    private String endCondition;
    private LocalDateTime playedAt;
    private Integer rounds;
    private List<GamePlayerDetailResponse> players;

    public GameDetailResponse() {
    }

    public GameDetailResponse(Long gameId, String roomCode, String mode, String secretWord, String winningSide,
                              String endCondition, LocalDateTime playedAt, Integer rounds,
                              List<GamePlayerDetailResponse> players) {
        this.gameId = gameId;
        this.roomCode = roomCode;
        this.mode = mode;
        this.secretWord = secretWord;
        this.winningSide = winningSide;
        this.endCondition = endCondition;
        this.playedAt = playedAt;
        this.rounds = rounds;
        this.players = players;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSecretWord() {
        return secretWord;
    }

    public void setSecretWord(String secretWord) {
        this.secretWord = secretWord;
    }

    public String getWinningSide() {
        return winningSide;
    }

    public void setWinningSide(String winningSide) {
        this.winningSide = winningSide;
    }

    public String getEndCondition() {
        return endCondition;
    }

    public void setEndCondition(String endCondition) {
        this.endCondition = endCondition;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }

    public Integer getRounds() {
        return rounds;
    }

    public void setRounds(Integer rounds) {
        this.rounds = rounds;
    }

    public List<GamePlayerDetailResponse> getPlayers() {
        return players;
    }

    public void setPlayers(List<GamePlayerDetailResponse> players) {
        this.players = players;
    }
}