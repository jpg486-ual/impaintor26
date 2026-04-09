package com.example.demo.game.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.game.model.DrawingStroke;

public interface DrawingStrokeRepository extends JpaRepository<DrawingStroke, Long> {
    List<DrawingStroke> findByRoomCodeAndRoundNumberOrderByIdAsc(String roomCode, int roundNumber);

    void deleteByRoomCodeAndRoundNumber(String roomCode, int roundNumber);

    void deleteByRoomCodeAndRoundNumberAndPlayer(String roomCode, int roundNumber, Long playerId);

    void deleteByRoomCode(String roomCode);
}
