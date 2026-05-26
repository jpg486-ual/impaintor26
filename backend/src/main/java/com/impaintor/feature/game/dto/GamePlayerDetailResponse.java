package com.impaintor.feature.game.dto;

public class GamePlayerDetailResponse {

    private Long userId;
    private String username;
    private boolean impostor;
    private boolean winner;
    private Integer eloChange;

    public GamePlayerDetailResponse() {
    }

    public GamePlayerDetailResponse(Long userId, String username, boolean impostor, boolean winner, Integer eloChange) {
        this.userId = userId;
        this.username = username;
        this.impostor = impostor;
        this.winner = winner;
        this.eloChange = eloChange;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isImpostor() {
        return impostor;
    }

    public void setImpostor(boolean impostor) {
        this.impostor = impostor;
    }

    public boolean isWinner() {
        return winner;
    }

    public void setWinner(boolean winner) {
        this.winner = winner;
    }

    public Integer getEloChange() {
        return eloChange;
    }

    public void setEloChange(Integer eloChange) {
        this.eloChange = eloChange;
    }
}