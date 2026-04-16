package com.example.demo.game.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.game.model.GamePlayer;

public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {
    List<GamePlayer> findByRoomCodeOrderByJoinedAtAsc(String roomCode);

    boolean existsByRoomCodeAndNameIgnoreCase(String roomCode, String name);

    int countByRoomCode(String roomCode);

    Optional<GamePlayer> findByIdAndRoomCode(Long id, String roomCode);

    Optional<GamePlayer> findByRoomCodeAndUserId(String roomCode, Long userId);

    void deleteByRoomCode(String roomCode);
}
