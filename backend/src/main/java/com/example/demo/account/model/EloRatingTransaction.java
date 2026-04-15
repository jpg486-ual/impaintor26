package com.example.demo.account.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "elo_rating_transactions")
public class EloRatingTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long rankedMatchId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int eloBefore;

    @Column(nullable = false)
    private int eloAfter;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private int kFactor;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRankedMatchId() {
        return rankedMatchId;
    }

    public void setRankedMatchId(Long rankedMatchId) {
        this.rankedMatchId = rankedMatchId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getEloBefore() {
        return eloBefore;
    }

    public void setEloBefore(int eloBefore) {
        this.eloBefore = eloBefore;
    }

    public int getEloAfter() {
        return eloAfter;
    }

    public void setEloAfter(int eloAfter) {
        this.eloAfter = eloAfter;
    }

    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public int getKFactor() {
        return kFactor;
    }

    public void setKFactor(int kFactor) {
        this.kFactor = kFactor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
