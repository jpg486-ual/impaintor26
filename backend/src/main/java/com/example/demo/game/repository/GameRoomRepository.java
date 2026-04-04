package com.example.demo.game.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.game.model.GameRoom;

public interface GameRoomRepository extends JpaRepository<GameRoom, String> {
    Optional<GameRoom> findByCode(String code);
}
