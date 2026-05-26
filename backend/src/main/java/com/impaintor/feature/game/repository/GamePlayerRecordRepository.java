package com.impaintor.feature.game.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.impaintor.feature.game.model.GamePlayerRecord;

@Repository
public interface GamePlayerRecordRepository extends JpaRepository<GamePlayerRecord, Long> {

	// Devuelve los registros de partidas de un usuario
	@EntityGraph(attributePaths = {"gameRecord"})
	Page<GamePlayerRecord> findByUser_Id(Long userId, Pageable pageable);

	@EntityGraph(attributePaths = {"gameRecord", "user"})
	List<GamePlayerRecord> findByGameRecord_Id(Long gameRecordId);
}
