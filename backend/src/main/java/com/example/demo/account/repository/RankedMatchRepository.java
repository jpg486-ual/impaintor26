package com.example.demo.account.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.account.model.RankedMatch;

public interface RankedMatchRepository extends JpaRepository<RankedMatch, Long> {
    Optional<RankedMatch> findByRoomCode(String roomCode);
}
