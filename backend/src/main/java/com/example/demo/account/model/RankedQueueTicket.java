package com.example.demo.account.model;

import java.time.Instant;

import com.example.demo.game.model.GameMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "ranked_queue_tickets")
public class RankedQueueTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameMode requestedGameMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RankedQueueTicketStatus status;

    @Column(nullable = false)
    private int eloAtQueueTime;

    @Column(nullable = false)
    private Instant queuedAt;

    @Column(nullable = true)
    private Integer lastSearchMinElo;

    @Column(nullable = true)
    private Integer lastSearchMaxElo;

    @Column(nullable = true, length = 6)
    private String matchedRoomCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = RankedQueueTicketStatus.QUEUED;
        }
        if (this.queuedAt == null) {
            this.queuedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public GameMode getRequestedGameMode() {
        return requestedGameMode;
    }

    public void setRequestedGameMode(GameMode requestedGameMode) {
        this.requestedGameMode = requestedGameMode;
    }

    public RankedQueueTicketStatus getStatus() {
        return status;
    }

    public void setStatus(RankedQueueTicketStatus status) {
        this.status = status;
    }

    public int getEloAtQueueTime() {
        return eloAtQueueTime;
    }

    public void setEloAtQueueTime(int eloAtQueueTime) {
        this.eloAtQueueTime = eloAtQueueTime;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(Instant queuedAt) {
        this.queuedAt = queuedAt;
    }

    public Integer getLastSearchMinElo() {
        return lastSearchMinElo;
    }

    public void setLastSearchMinElo(Integer lastSearchMinElo) {
        this.lastSearchMinElo = lastSearchMinElo;
    }

    public Integer getLastSearchMaxElo() {
        return lastSearchMaxElo;
    }

    public void setLastSearchMaxElo(Integer lastSearchMaxElo) {
        this.lastSearchMaxElo = lastSearchMaxElo;
    }

    public String getMatchedRoomCode() {
        return matchedRoomCode;
    }

    public void setMatchedRoomCode(String matchedRoomCode) {
        this.matchedRoomCode = matchedRoomCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
