package com.example.demo.game.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_rooms")
public class GameRoom {

    @Id
    @Column(length = 6)
    private String code;

    @Column(nullable = false)
    private String hostName;

    @Column(nullable = false)
    private int roundDurationSeconds;

    @Column(nullable = false)
    private int votingDurationSeconds;

    @Column(nullable = false)
    private int maxRounds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameMode gameMode;

    @Column(nullable = false, length = 512)
    private String themesCsv;

    @Column(nullable = false)
    private int currentRound;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GamePhase phase;

    @Column(nullable = true)
    private Instant phaseEndsAt;

    @Column(nullable = true)
    private Instant finishedAt;

    @Column(nullable = true, length = 64)
    private String currentWord;

    @Column(nullable = true)
    private Long impostorPlayerId;

    @Column(nullable = true)
    private Long activeDrawerPlayerId;

    @Column(nullable = false)
    private int activeDrawerTurnIndex;

    @Column(nullable = false)
    private int turnsCompletedInRound;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }

    public void setRoundDurationSeconds(int roundDurationSeconds) {
        this.roundDurationSeconds = roundDurationSeconds;
    }

    public int getVotingDurationSeconds() {
        return votingDurationSeconds;
    }

    public void setVotingDurationSeconds(int votingDurationSeconds) {
        this.votingDurationSeconds = votingDurationSeconds;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public String getThemesCsv() {
        return themesCsv;
    }

    public void setThemesCsv(String themesCsv) {
        this.themesCsv = themesCsv;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public Instant getPhaseEndsAt() {
        return phaseEndsAt;
    }

    public void setPhaseEndsAt(Instant phaseEndsAt) {
        this.phaseEndsAt = phaseEndsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getCurrentWord() {
        return currentWord;
    }

    public void setCurrentWord(String currentWord) {
        this.currentWord = currentWord;
    }

    public Long getImpostorPlayerId() {
        return impostorPlayerId;
    }

    public void setImpostorPlayerId(Long impostorPlayerId) {
        this.impostorPlayerId = impostorPlayerId;
    }

    public Long getActiveDrawerPlayerId() {
        return activeDrawerPlayerId;
    }

    public void setActiveDrawerPlayerId(Long activeDrawerPlayerId) {
        this.activeDrawerPlayerId = activeDrawerPlayerId;
    }

    public int getActiveDrawerTurnIndex() {
        return activeDrawerTurnIndex;
    }

    public void setActiveDrawerTurnIndex(int activeDrawerTurnIndex) {
        this.activeDrawerTurnIndex = activeDrawerTurnIndex;
    }

    public int getTurnsCompletedInRound() {
        return turnsCompletedInRound;
    }

    public void setTurnsCompletedInRound(int turnsCompletedInRound) {
        this.turnsCompletedInRound = turnsCompletedInRound;
    }
}
