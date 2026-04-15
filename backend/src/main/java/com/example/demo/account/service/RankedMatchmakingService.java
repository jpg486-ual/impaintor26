package com.example.demo.account.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.account.dto.MatchmakingDtos;
import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.RankedQueueTicket;
import com.example.demo.account.model.RankedQueueTicketStatus;
import com.example.demo.account.model.UserRankProfile;
import com.example.demo.account.model.UserStatus;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.repository.RankedQueueTicketRepository;
import com.example.demo.game.model.GameMode;

@Service
public class RankedMatchmakingService {

    private static final int BASE_ELO_WINDOW = 100;
    private static final int WINDOW_EXPANSION_STEP = 100;
    private static final int WINDOW_EXPANSION_INTERVAL_SECONDS = 10;

    private final AppUserRepository userRepository;
    private final RankedQueueTicketRepository queueTicketRepository;
    private final RatingService ratingService;

    public RankedMatchmakingService(
            AppUserRepository userRepository,
            RankedQueueTicketRepository queueTicketRepository,
            RatingService ratingService) {
        this.userRepository = userRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.ratingService = ratingService;
    }

    @Transactional
    public MatchmakingDtos.PublicQueueStatusResponse enqueue(long userId, GameMode gameMode) {
        validateActiveUser(userId);
        UserRankProfile profile = ratingService.getOrCreateProfile(userId);

        RankedQueueTicket ticket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.QUEUED)
                .orElseGet(() -> createQueueTicket(userId, gameMode, profile.getElo()));

        if (ticket.getRequestedGameMode() != gameMode) {
            ticket.setRequestedGameMode(gameMode);
        }

        refreshSearchRange(ticket, Instant.now());
        queueTicketRepository.save(ticket);
        return toStatusResponse(ticket, true, Instant.now());
    }

    @Transactional
    public void leaveQueue(long userId) {
        RankedQueueTicket ticket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.QUEUED)
                .orElse(null);

        if (ticket == null) {
            return;
        }

        ticket.setStatus(RankedQueueTicketStatus.CANCELLED);
        queueTicketRepository.save(ticket);
    }

    @Transactional
    public MatchmakingDtos.PublicQueueStatusResponse getStatus(long userId) {
        RankedQueueTicket ticket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.QUEUED)
                .orElse(null);

        if (ticket == null) {
            return new MatchmakingDtos.PublicQueueStatusResponse(
                    false, null, userId, null, null, 0, null, null, 0, null);
        }

        Instant now = Instant.now();
        refreshSearchRange(ticket, now);
        queueTicketRepository.save(ticket);
        return toStatusResponse(ticket, true, now);
    }

    private RankedQueueTicket createQueueTicket(long userId, GameMode gameMode, int eloAtQueueTime) {
        RankedQueueTicket ticket = new RankedQueueTicket();
        ticket.setUserId(userId);
        ticket.setRequestedGameMode(gameMode);
        ticket.setStatus(RankedQueueTicketStatus.QUEUED);
        ticket.setEloAtQueueTime(eloAtQueueTime);
        ticket.setQueuedAt(Instant.now());
        return queueTicketRepository.save(ticket);
    }

    private void validateActiveUser(long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no disponible para ranked");
        }
    }

    private void refreshSearchRange(RankedQueueTicket ticket, Instant now) {
        long waitedSeconds = Math.max(0, Duration.between(ticket.getQueuedAt(), now).getSeconds());
        int expansions = (int) (waitedSeconds / WINDOW_EXPANSION_INTERVAL_SECONDS);
        int window = BASE_ELO_WINDOW + (expansions * WINDOW_EXPANSION_STEP);
        ticket.setLastSearchMinElo(ticket.getEloAtQueueTime() - window);
        ticket.setLastSearchMaxElo(ticket.getEloAtQueueTime() + window);
    }

    private MatchmakingDtos.PublicQueueStatusResponse toStatusResponse(
            RankedQueueTicket ticket,
            boolean queued,
            Instant now) {
        long waitedSeconds = Math.max(0, Duration.between(ticket.getQueuedAt(), now).getSeconds());
        return new MatchmakingDtos.PublicQueueStatusResponse(
                queued,
                ticket.getId(),
                ticket.getUserId(),
                ticket.getRequestedGameMode(),
                ticket.getStatus(),
                ticket.getEloAtQueueTime(),
                ticket.getLastSearchMinElo(),
                ticket.getLastSearchMaxElo(),
                waitedSeconds,
                ticket.getQueuedAt());
    }
}
