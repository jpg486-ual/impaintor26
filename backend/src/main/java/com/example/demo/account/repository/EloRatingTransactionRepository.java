package com.example.demo.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.account.model.EloRatingTransaction;

public interface EloRatingTransactionRepository extends JpaRepository<EloRatingTransaction, Long> {
    List<EloRatingTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<EloRatingTransaction> findByRankedMatchIdOrderByCreatedAtAsc(Long rankedMatchId);
}
