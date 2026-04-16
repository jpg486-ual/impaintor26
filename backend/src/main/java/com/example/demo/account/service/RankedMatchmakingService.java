package com.example.demo.account.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import com.example.demo.game.repository.GamePlayerRepository;
import com.example.demo.game.service.GameService;

@Service
public class RankedMatchmakingService {

    private static final int BASE_ELO_WINDOW = 100;
    private static final int WINDOW_EXPANSION_STEP = 100;
    private static final int WINDOW_EXPANSION_INTERVAL_SECONDS = 10;
    private static final int MATCH_CONFIRMATION_WINDOW_SECONDS = 15;
    private static final int MATCH_SIZE = 3;

    private final AppUserRepository userRepository;
    private final RankedQueueTicketRepository queueTicketRepository;
    private final RankedMatchRepository rankedMatchRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RatingService ratingService;
    private final GameService gameService;

    public RankedMatchmakingService(
            AppUserRepository userRepository,
            RankedQueueTicketRepository queueTicketRepository,
            RankedMatchRepository rankedMatchRepository,
            GamePlayerRepository gamePlayerRepository,
            RatingService ratingService,
            GameService gameService) {
        this.userRepository = userRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.rankedMatchRepository = rankedMatchRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.ratingService = ratingService;
        this.gameService = gameService;
    }

    @Transactional
    public MatchmakingDtos.PublicPlayerProfileResponse bootstrapPublicPlayer(String username) {
        String normalizedUsername = normalizeUsername(username);
        AppUser user = userRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseGet(() -> createBootstrapUser(normalizedUsername));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no disponible para ranked");
        }

        UserRankProfile profile = ratingService.getOrCreateProfile(user.getId());
        return new MatchmakingDtos.PublicPlayerProfileResponse(
                user.getId(),
                user.getUsername(),
                profile.getElo(),
                profile.getRankedGamesPlayed(),
                profile.getProvisionalMatchesRemaining());
    }

    @Transactional
    public MatchmakingDtos.PublicQueueStatusResponse enqueue(long userId, GameMode gameMode) {
        if (gameMode != GameMode.TURN_BASED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Las partidas ranked solo permiten modo por turnos");
        }
        validateActiveUser(userId);

        Instant now = Instant.now();
        RankedQueueTicket pendingTicket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION)
                .orElse(null);
        if (pendingTicket != null) {
            if (isPendingConfirmationExpired(pendingTicket, now)) {
                expirePendingMatch(pendingTicket.getRankedMatchId(), now);
                return getStatus(userId);
            }
            return toStatusResponse(pendingTicket, true, now);
        }

        UserRankProfile profile = ratingService.getOrCreateProfile(userId);

        RankedQueueTicket ticket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.QUEUED)
                .orElseGet(() -> createQueueTicket(userId, gameMode, profile.getElo()));

        if (ticket.getRequestedGameMode() != gameMode) {
            ticket.setRequestedGameMode(gameMode);
        }
        ticket.setMatchedRoomCode(null);
        ticket.setRankedMatchId(null);
        ticket.setConfirmationDeadlineAt(null);
        ticket.setConfirmedAt(null);

        refreshSearchRange(ticket, now);
        queueTicketRepository.save(ticket);
        return toStatusResponse(ticket, true, now);
    }

    @Transactional
    public void leaveQueue(long userId) {
        Instant now = Instant.now();
        RankedQueueTicket pendingTicket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION)
                .orElse(null);

        if (pendingTicket != null) {
            if (pendingTicket.getRankedMatchId() != null) {
                cancelPendingMatch(pendingTicket.getRankedMatchId(), RankedQueueTicketStatus.CANCELLED, now);
            } else {
                pendingTicket.setStatus(RankedQueueTicketStatus.CANCELLED);
                queueTicketRepository.save(pendingTicket);
            }
            return;
        }

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
        Instant now = Instant.now();
        RankedQueueTicket pendingTicket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION)
                .orElse(null);

        if (pendingTicket != null) {
            if (isPendingConfirmationExpired(pendingTicket, now)) {
                expirePendingMatch(pendingTicket.getRankedMatchId(), now);
            } else {
                return toStatusResponse(pendingTicket, true, now);
            }
        }

        RankedQueueTicket queuedTicket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.QUEUED)
                .orElse(null);

        if (queuedTicket != null) {
            refreshSearchRange(queuedTicket, now);
            queueTicketRepository.save(queuedTicket);
            return toStatusResponse(queuedTicket, true, now);
        }

        RankedQueueTicket latestTicket = queueTicketRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        if (latestTicket == null) {
            return new MatchmakingDtos.PublicQueueStatusResponse(
                    false, null, userId, null, null, 0, null, null, 0, null, null, null, null, null, false);
        }
        return toStatusResponse(latestTicket, false, now);
    }

    @Transactional
    public MatchmakingDtos.PublicQueueStatusResponse confirmMatch(long userId) {
        Instant now = Instant.now();
        RankedQueueTicket pendingTicket = queueTicketRepository
                .findByUserIdAndStatus(userId, RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION)
                .orElse(null);

        if (pendingTicket == null) {
            return getStatus(userId);
        }

        if (isPendingConfirmationExpired(pendingTicket, now)) {
            expirePendingMatch(pendingTicket.getRankedMatchId(), now);
            return getStatus(userId);
        }

        if (pendingTicket.getConfirmedAt() == null) {
            pendingTicket.setConfirmedAt(now);
            queueTicketRepository.save(pendingTicket);
        }

        finalizePendingMatchIfFullyConfirmed(pendingTicket.getRankedMatchId(), pendingTicket.getRequestedGameMode(), now);
        return getStatus(userId);
    }

    @Transactional
    public int processQueueCycle() {
        Instant now = Instant.now();
        expirePendingConfirmations(now);
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

            if (openPendingConfirmationForGroup(mode, maybeGroup.get(), now)) {
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

    private boolean openPendingConfirmationForGroup(GameMode gameMode, List<RankedQueueTicket> group, Instant now) {
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
        rankedMatch.setStatus(RankedMatchStatus.WAITING_CONFIRMATION);
        rankedMatch = rankedMatchRepository.save(rankedMatch);

        Instant confirmationDeadline = now.plusSeconds(MATCH_CONFIRMATION_WINDOW_SECONDS);
        for (RankedQueueTicket ticket : group) {
            ticket.setStatus(RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION);
            ticket.setRankedMatchId(rankedMatch.getId());
            ticket.setMatchedRoomCode(null);
            ticket.setConfirmationDeadlineAt(confirmationDeadline);
            ticket.setConfirmedAt(null);
        }
        queueTicketRepository.saveAll(group);
        return true;
    }

    private void finalizePendingMatchIfFullyConfirmed(Long rankedMatchId, GameMode gameMode, Instant now) {
        if (rankedMatchId == null) {
            return;
        }

        List<RankedQueueTicket> pendingTickets = queueTicketRepository
                .findByRankedMatchIdAndStatusOrderByQueuedAtAsc(
                        rankedMatchId,
                        RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION);
        if (pendingTickets.size() != MATCH_SIZE) {
            return;
        }

        if (pendingTickets.stream().anyMatch(ticket -> isPendingConfirmationExpired(ticket, now))) {
            expirePendingMatch(rankedMatchId, now);
            return;
        }

        if (pendingTickets.stream().anyMatch(ticket -> ticket.getConfirmedAt() == null)) {
            return;
        }

        RankedMatch rankedMatch = rankedMatchRepository.findById(rankedMatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida ranked no encontrada"));
        if (rankedMatch.getStatus() == RankedMatchStatus.IN_PROGRESS || rankedMatch.getStatus() == RankedMatchStatus.FINISHED) {
            return;
        }
        rankedMatch.setStatus(RankedMatchStatus.PENDING_ROOM_ASSIGNMENT);
        rankedMatchRepository.save(rankedMatch);

        Map<Long, AppUser> usersById = new HashMap<>();
        List<AppUser> users = userRepository.findAllById(pendingTickets.stream().map(RankedQueueTicket::getUserId).toList());
        for (AppUser user : users) {
            usersById.put(user.getId(), user);
        }

        boolean hasInvalidUser = pendingTickets.stream().anyMatch(ticket -> {
            AppUser user = usersById.get(ticket.getUserId());
            return user == null || user.getStatus() != UserStatus.ACTIVE;
        });
        if (hasInvalidUser) {
            expirePendingMatch(rankedMatchId, now);
            return;
        }

        List<GameService.RankedPlayerSeed> rankedPlayers = pendingTickets.stream()
                .map(ticket -> {
                    AppUser user = usersById.get(ticket.getUserId());
                    return new GameService.RankedPlayerSeed(ticket.getUserId(), user.getUsername());
                })
                .toList();

        String roomCode = gameService.createRankedRoom(rankedMatch.getId(), gameMode, rankedPlayers);
        rankedMatch.setRoomCode(roomCode);
        rankedMatch.setStatus(RankedMatchStatus.IN_PROGRESS);
        rankedMatch.setStartedAt(now);
        rankedMatchRepository.save(rankedMatch);

        for (RankedQueueTicket ticket : pendingTickets) {
            ticket.setStatus(RankedQueueTicketStatus.MATCHED);
            ticket.setMatchedRoomCode(roomCode);
        }
        queueTicketRepository.saveAll(pendingTickets);
    }

    private void expirePendingConfirmations(Instant now) {
        List<RankedQueueTicket> expiredTickets = queueTicketRepository
                .findByStatusAndConfirmationDeadlineAtBefore(
                        RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION,
                        now);
        if (expiredTickets.isEmpty()) {
            return;
        }

        Set<Long> processedMatchIds = new HashSet<>();
        for (RankedQueueTicket ticket : expiredTickets) {
            Long rankedMatchId = ticket.getRankedMatchId();
            if (rankedMatchId == null) {
                ticket.setStatus(RankedQueueTicketStatus.EXPIRED);
                queueTicketRepository.save(ticket);
                continue;
            }
            if (processedMatchIds.add(rankedMatchId)) {
                expirePendingMatch(rankedMatchId, now);
            }
        }
    }

    private void expirePendingMatch(Long rankedMatchId, Instant now) {
        if (rankedMatchId == null) {
            return;
        }

        List<RankedQueueTicket> pendingTickets = queueTicketRepository
                .findByRankedMatchIdAndStatusOrderByQueuedAtAsc(
                        rankedMatchId,
                        RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION);

        Map<Long, AppUser> usersById = new HashMap<>();
        List<AppUser> users = userRepository.findAllById(pendingTickets.stream().map(RankedQueueTicket::getUserId).toList());
        for (AppUser user : users) {
            usersById.put(user.getId(), user);
        }

        for (RankedQueueTicket ticket : pendingTickets) {
            AppUser user = usersById.get(ticket.getUserId());
            boolean shouldRequeue = ticket.getConfirmedAt() != null
                    && user != null
                    && user.getStatus() == UserStatus.ACTIVE;

            if (shouldRequeue) {
                ticket.setStatus(RankedQueueTicketStatus.QUEUED);
                ticket.setQueuedAt(now);
                ticket.setMatchedRoomCode(null);
                ticket.setRankedMatchId(null);
                ticket.setConfirmationDeadlineAt(null);
                ticket.setConfirmedAt(null);
            } else {
                ticket.setStatus(RankedQueueTicketStatus.EXPIRED);
                ticket.setConfirmationDeadlineAt(ticket.getConfirmationDeadlineAt() == null ? now : ticket.getConfirmationDeadlineAt());
            }
        }
        queueTicketRepository.saveAll(pendingTickets);

        rankedMatchRepository.findById(rankedMatchId).ifPresent(match -> {
            if (match.getStatus() != RankedMatchStatus.FINISHED && match.getStatus() != RankedMatchStatus.IN_PROGRESS) {
                match.setStatus(RankedMatchStatus.CANCELLED);
                rankedMatchRepository.save(match);
            }
        });
    }

    private void cancelPendingMatch(Long rankedMatchId, RankedQueueTicketStatus terminalStatus, Instant now) {
        if (rankedMatchId == null) {
            return;
        }
        List<RankedQueueTicket> pendingTickets = queueTicketRepository
                .findByRankedMatchIdAndStatusOrderByQueuedAtAsc(
                        rankedMatchId,
                        RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION);
        for (RankedQueueTicket ticket : pendingTickets) {
            ticket.setStatus(terminalStatus);
            ticket.setConfirmationDeadlineAt(ticket.getConfirmationDeadlineAt() == null ? now : ticket.getConfirmationDeadlineAt());
        }
        queueTicketRepository.saveAll(pendingTickets);

        rankedMatchRepository.findById(rankedMatchId).ifPresent(match -> {
            if (match.getStatus() != RankedMatchStatus.FINISHED && match.getStatus() != RankedMatchStatus.IN_PROGRESS) {
                match.setStatus(RankedMatchStatus.CANCELLED);
                rankedMatchRepository.save(match);
            }
        });
    }

    private boolean isPendingConfirmationExpired(RankedQueueTicket ticket, Instant now) {
        Instant deadline = ticket.getConfirmationDeadlineAt();
        return deadline != null && !deadline.isAfter(now);
    }

    private void validateActiveUser(long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no disponible para ranked");
        }
    }

    private AppUser createBootstrapUser(String normalizedUsername) {
        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setEmail(buildUniqueBootstrapEmail(normalizedUsername));
        user.setPasswordHash("public-ranked-bootstrap");
        return userRepository.save(user);
    }

    private String normalizeUsername(String rawUsername) {
        if (rawUsername == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de usuario inválido");
        }
        String normalized = rawUsername.trim();
        if (normalized.length() < 2 || normalized.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de usuario inválido");
        }
        return normalized;
    }

    private String buildUniqueBootstrapEmail(String username) {
        String base = username.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^[-.]+|[-.]+$)", "");
        if (base.isBlank()) {
            base = "player";
        }

        String candidate = base + "@ranked.local";
        if (!userRepository.existsByEmailIgnoreCase(candidate)) {
            return candidate;
        }

        int suffix = 2;
        while (true) {
            String withSuffix = base + "-" + suffix + "@ranked.local";
            if (!userRepository.existsByEmailIgnoreCase(withSuffix)) {
                return withSuffix;
            }
            suffix++;
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
        Long matchedPlayerId = resolveMatchedPlayerId(ticket);
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
                ticket.getMatchedRoomCode(),
                matchedPlayerId,
                ticket.getRankedMatchId(),
                ticket.getConfirmationDeadlineAt(),
                ticket.getConfirmedAt() != null);
    }

    private Long resolveMatchedPlayerId(RankedQueueTicket ticket) {
        if (ticket.getMatchedRoomCode() == null || ticket.getUserId() == null) {
            return null;
        }
        return gamePlayerRepository.findByRoomCodeAndUserId(ticket.getMatchedRoomCode(), ticket.getUserId())
                .map(player -> player.getId())
                .orElse(null);
    }
}
