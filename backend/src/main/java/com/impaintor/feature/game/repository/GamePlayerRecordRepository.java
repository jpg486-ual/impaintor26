package com.impaintor.feature.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.impaintor.feature.game.models.GamePlayerRecord;

@Repository
public interface GamePlayerRecordRepository extends JpaRepository<GamePlayerRecord, Long> {
}
