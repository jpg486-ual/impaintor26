package com.example.demo.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.account.model.UserRankProfile;

public interface UserRankProfileRepository extends JpaRepository<UserRankProfile, Long> {
}
