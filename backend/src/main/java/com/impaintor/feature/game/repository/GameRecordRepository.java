package com.impaintor.feature.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.impaintor.feature.game.models.GameRecord;

@Repository
public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {
}
