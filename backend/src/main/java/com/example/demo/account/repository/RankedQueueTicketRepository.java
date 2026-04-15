package com.example.demo.account.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.account.model.RankedQueueTicket;
import com.example.demo.account.model.RankedQueueTicketStatus;

public interface RankedQueueTicketRepository extends JpaRepository<RankedQueueTicket, Long> {
    Optional<RankedQueueTicket> findByUserIdAndStatus(Long userId, RankedQueueTicketStatus status);

    List<RankedQueueTicket> findByStatusOrderByQueuedAtAsc(RankedQueueTicketStatus status);
}
