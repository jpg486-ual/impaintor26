package com.example.demo.account.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.account.model.RankedQueueTicket;
import com.example.demo.account.model.RankedQueueTicketStatus;
import com.example.demo.game.model.GameMode;

public interface RankedQueueTicketRepository extends JpaRepository<RankedQueueTicket, Long> {
    Optional<RankedQueueTicket> findByUserIdAndStatus(Long userId, RankedQueueTicketStatus status);

    Optional<RankedQueueTicket> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);

    List<RankedQueueTicket> findByStatusAndRequestedGameModeOrderByQueuedAtAsc(
            RankedQueueTicketStatus status,
            GameMode requestedGameMode);

    List<RankedQueueTicket> findByStatusOrderByQueuedAtAsc(RankedQueueTicketStatus status);

    List<RankedQueueTicket> findByRankedMatchIdAndStatusOrderByQueuedAtAsc(Long rankedMatchId, RankedQueueTicketStatus status);

    List<RankedQueueTicket> findByStatusAndConfirmationDeadlineAtBefore(
            RankedQueueTicketStatus status,
            Instant confirmationDeadlineAt);
}
