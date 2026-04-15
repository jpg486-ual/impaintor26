package com.example.demo.account.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_rank_profiles")
public class UserRankProfile {

    public static final int DEFAULT_ELO = 1200;
    public static final int DEFAULT_PROVISIONAL_MATCHES = 10;

    @Id
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int elo;

    @Column(nullable = false)
    private int rankedGamesPlayed;

    @Column(nullable = false)
    private int provisionalMatchesRemaining;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.elo == 0) {
            this.elo = DEFAULT_ELO;
        }
        if (this.provisionalMatchesRemaining == 0) {
            this.provisionalMatchesRemaining = DEFAULT_PROVISIONAL_MATCHES;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getElo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = elo;
    }

    public int getRankedGamesPlayed() {
        return rankedGamesPlayed;
    }

    public void setRankedGamesPlayed(int rankedGamesPlayed) {
        this.rankedGamesPlayed = rankedGamesPlayed;
    }

    public int getProvisionalMatchesRemaining() {
        return provisionalMatchesRemaining;
    }

    public void setProvisionalMatchesRemaining(int provisionalMatchesRemaining) {
        this.provisionalMatchesRemaining = provisionalMatchesRemaining;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
