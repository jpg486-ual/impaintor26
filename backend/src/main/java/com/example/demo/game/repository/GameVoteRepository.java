package com.example.demo.game.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.game.model.GameVote;

public interface GameVoteRepository extends JpaRepository<GameVote, Long> {
    List<GameVote> findByRoomCodeAndRoundNumber(String roomCode, int roundNumber);

    Optional<GameVote> findByRoomCodeAndRoundNumberAndVoterPlayerId(String roomCode, int roundNumber, Long voterPlayerId);

    int countByRoomCodeAndRoundNumber(String roomCode, int roundNumber);

    void deleteByRoomCode(String roomCode);
}
