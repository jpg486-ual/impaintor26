package com.example.demo.game.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "game_votes", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "roomCode", "roundNumber", "voterPlayerId" })
})
public class GameVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6)
    private String roomCode;

    @Column(nullable = false)
    private int roundNumber;

    @Column(nullable = false)
    private Long voterPlayerId;

    @Column(nullable = false)
    private Long targetPlayerId;

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public Long getVoterPlayerId() {
        return voterPlayerId;
    }

    public void setVoterPlayerId(Long voterPlayerId) {
        this.voterPlayerId = voterPlayerId;
    }

    public Long getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(Long targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }
}
