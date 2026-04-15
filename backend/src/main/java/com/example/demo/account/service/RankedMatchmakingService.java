package com.example.demo.account.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.account.dto.MatchmakingDtos;
import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.RankedMatch;
import com.example.demo.account.model.RankedMatchStatus;
import com.example.demo.account.model.RankedQueueTicket;
import com.example.demo.account.model.RankedQueueTicketStatus;
import com.example.demo.account.model.UserRankProfile;
import com.example.demo.account.model.UserStatus;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.repository.RankedMatchRepository;
import com.example.demo.account.repository.RankedQueueTicketRepository;
import com.example.demo.game.model.GameMode;
import com.example.demo.game.service.GameService;

@Service
public class RankedMatchmakingService {

    private static final int BASE_ELO_WINDOW = 100;
    private static final int WINDOW_EXPANSION_STEP = 100;
    private static final int WINDOW_EXPANSION_INTERVAL_SECONDS = 10;
    private static final int MATCH_SIZE = 3;

    private final AppUserRepository userRepository;
    private final RankedQueueTicketRepository queueTicketRepository;
    private final RankedMatchRepository rankedMatchRepository;
    private final RatingService ratingService;
    private final GameService gameService;

    public RankedMatchmakingService(
            AppUserRepository userRepository,
            RankedQueueTicketRepository queueTicketRepository,
            RankedMatchRepository rankedMatchRepository,
            RatingService ratingService,
            GameService gameService) {
        this.userRepository = userRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.rankedMatchRepository = rankedMatchRepository;
        this.ratingService = ratingService;
        this.gameService = gameService;
    }

    @Transactional
    public MatchmakingDtos.PublicQueueStatusResponse enqueue(long userId, GameMode gameMode) {
        if (gameMode != GameMode.TURN_BASED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Las partidas ranked solo permiten modo por turnos");
        }
        validateActiveUser(userId);
        UserRankProfile profile = ratingService.getOrCreateProfile(userId);

        RankedQueueTicket ticket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.QUEUED)
                .orElseGet(() -> createQueueTicket(userId, gameMode, profile.getElo()));

        if (ticket.getRequestedGameMode() != gameMode) {
            ticket.setRequestedGameMode(gameMode);
        }
        ticket.setMatchedRoomCode(null);

        Instant now = Instant.now();
        refreshSearchRange(ticket, now);
        queueTicketRepository.save(ticket);
        return toStatusResponse(ticket, true, now);
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
        RankedQueueTicket queuedTicket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.QUEUED)
                .orElse(null);

        if (queuedTicket != null) {
            Instant now = Instant.now();
            refreshSearchRange(queuedTicket, now);
            queueTicketRepository.save(queuedTicket);
            return toStatusResponse(queuedTicket, true, now);
        }

        RankedQueueTicket latestTicket = queueTicketRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        if (latestTicket == null) {
            return new MatchmakingDtos.PublicQueueStatusResponse(
                    false, null, userId, null, null, 0, null, null, 0, null, null);
        }
        return toStatusResponse(latestTicket, false, Instant.now());
    }

    @Transactional
    public int processQueueCycle() {
        Instant now = Instant.now();
        return processModeQueue(GameMode.TURN_BASED, now);
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

    private int processModeQueue(GameMode mode, Instant now) {
        int matchesCreated = 0;
        while (true) {
            List<RankedQueueTicket> queuedTickets = queueTicketRepository
                    .findByStatusAndRequestedGameModeOrderByQueuedAtAsc(RankedQueueTicketStatus.QUEUED, mode);
            if (queuedTickets.size() < MATCH_SIZE) {
                return matchesCreated;
            }

            refreshSearchRanges(queuedTickets, now);
            queueTicketRepository.saveAll(queuedTickets);

            Optional<List<RankedQueueTicket>> maybeGroup = findNextCompatibleGroup(queuedTickets);
            if (maybeGroup.isEmpty()) {
                return matchesCreated;
            }

            if (createMatchForGroup(mode, maybeGroup.get())) {
                matchesCreated++;
            }
        }
    }

    private Optional<List<RankedQueueTicket>> findNextCompatibleGroup(List<RankedQueueTicket> queuedTickets) {
        for (int i = 0; i < queuedTickets.size(); i++) {
            List<RankedQueueTicket> group = new java.util.ArrayList<>();
            RankedQueueTicket anchor = queuedTickets.get(i);
            group.add(anchor);

            for (int j = i + 1; j < queuedTickets.size() && group.size() < MATCH_SIZE; j++) {
                RankedQueueTicket candidate = queuedTickets.get(j);
                boolean compatible = group.stream().allMatch(current -> isMutuallyCompatible(current, candidate));
                if (compatible) {
                    group.add(candidate);
                }
            }

            if (group.size() == MATCH_SIZE) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    private boolean createMatchForGroup(GameMode gameMode, List<RankedQueueTicket> group) {
        Map<Long, AppUser> usersById = new HashMap<>();
        List<AppUser> users = userRepository.findAllById(group.stream().map(RankedQueueTicket::getUserId).toList());
        for (AppUser user : users) {
            usersById.put(user.getId(), user);
        }

        List<RankedQueueTicket> invalidTickets = group.stream()
                .filter(ticket -> {
                    AppUser user = usersById.get(ticket.getUserId());
                    return user == null || user.getStatus() != UserStatus.ACTIVE;
                })
                .toList();
        if (!invalidTickets.isEmpty()) {
            for (RankedQueueTicket invalidTicket : invalidTickets) {
                invalidTicket.setStatus(RankedQueueTicketStatus.EXPIRED);
            }
            queueTicketRepository.saveAll(invalidTickets);
            return false;
        }

        RankedMatch rankedMatch = new RankedMatch();
        rankedMatch.setStatus(RankedMatchStatus.PENDING_ROOM_ASSIGNMENT);
        rankedMatch = rankedMatchRepository.save(rankedMatch);

        List<GameService.RankedPlayerSeed> rankedPlayers = group.stream()
                .map(ticket -> {
                    AppUser user = usersById.get(ticket.getUserId());
                    return new GameService.RankedPlayerSeed(ticket.getUserId(), user.getUsername());
                })
                .toList();

        String roomCode = gameService.createRankedRoom(rankedMatch.getId(), gameMode, rankedPlayers);
        rankedMatch.setRoomCode(roomCode);
        rankedMatch.setStatus(RankedMatchStatus.IN_PROGRESS);
        rankedMatch.setStartedAt(Instant.now());
        rankedMatchRepository.save(rankedMatch);

        for (RankedQueueTicket ticket : group) {
            ticket.setStatus(RankedQueueTicketStatus.MATCHED);
            ticket.setMatchedRoomCode(roomCode);
        }
        queueTicketRepository.saveAll(group);
        return true;
    }

    private void validateActiveUser(long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no disponible para ranked");
        }
    }

    private boolean isMutuallyCompatible(RankedQueueTicket left, RankedQueueTicket right) {
        int leftElo = left.getEloAtQueueTime();
        int rightElo = right.getEloAtQueueTime();
        return rightElo >= left.getLastSearchMinElo()
                && rightElo <= left.getLastSearchMaxElo()
                && leftElo >= right.getLastSearchMinElo()
                && leftElo <= right.getLastSearchMaxElo();
    }

    private void refreshSearchRanges(List<RankedQueueTicket> tickets, Instant now) {
        for (RankedQueueTicket ticket : tickets) {
            refreshSearchRange(ticket, now);
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
                ticket.getQueuedAt(),
                ticket.getMatchedRoomCode());
    }
}
