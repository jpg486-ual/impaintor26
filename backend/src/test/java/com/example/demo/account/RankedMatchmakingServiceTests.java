package com.example.demo.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.account.dto.MatchmakingDtos;
import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.RankedMatchStatus;
import com.example.demo.account.model.RankedQueueTicket;
import com.example.demo.account.model.RankedQueueTicketStatus;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.repository.RankedMatchRepository;
import com.example.demo.account.repository.RankedQueueTicketRepository;
import com.example.demo.account.repository.UserRankProfileRepository;
import com.example.demo.account.service.RankedMatchmakingService;
import com.example.demo.game.model.GameMode;

@SpringBootTest
@Transactional
class RankedMatchmakingServiceTests {

    @Autowired
    private RankedMatchmakingService matchmakingService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RankedQueueTicketRepository queueTicketRepository;

    @Autowired
    private RankedMatchRepository rankedMatchRepository;

    @Autowired
    private UserRankProfileRepository profileRepository;

    @Test
    void enqueue_createsQueuedTicketWithInitialWindow() {
        AppUser user = createUser("queue-user", "queue@example.com");
        ensureProfileElo(user.getId(), 1325);

        MatchmakingDtos.PublicQueueStatusResponse response = matchmakingService.enqueue(user.getId(), GameMode.TURN_BASED);

        assertThat(response.queued()).isTrue();
        assertThat(response.status()).isEqualTo(RankedQueueTicketStatus.QUEUED);
        assertThat(response.eloAtQueueTime()).isEqualTo(1325);
        assertThat(response.searchMinElo()).isEqualTo(1225);
        assertThat(response.searchMaxElo()).isEqualTo(1425);
    }

    @Test
    void bootstrapPublicPlayer_createsUserAndDefaultRankProfile() {
        MatchmakingDtos.PublicPlayerProfileResponse response = matchmakingService
                .bootstrapPublicPlayer("RankedBootstrapper");

        assertThat(response.userId()).isPositive();
        assertThat(response.username()).isEqualTo("RankedBootstrapper");
        assertThat(response.elo()).isEqualTo(com.example.demo.account.model.UserRankProfile.DEFAULT_ELO);
        assertThat(response.rankedGamesPlayed()).isZero();
        assertThat(response.provisionalMatchesRemaining())
                .isEqualTo(com.example.demo.account.model.UserRankProfile.DEFAULT_PROVISIONAL_MATCHES);
    }

    @Test
    void bootstrapPublicPlayer_isIdempotentByUsername() {
        MatchmakingDtos.PublicPlayerProfileResponse first = matchmakingService.bootstrapPublicPlayer("ranked-idem");
        MatchmakingDtos.PublicPlayerProfileResponse second = matchmakingService.bootstrapPublicPlayer("RANKED-IDEM");

        assertThat(first.userId()).isEqualTo(second.userId());
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void enqueue_isIdempotentForQueuedUser() {
        AppUser user = createUser("queue-repeat", "queue-repeat@example.com");

        MatchmakingDtos.PublicQueueStatusResponse first = matchmakingService.enqueue(user.getId(), GameMode.TURN_BASED);
        MatchmakingDtos.PublicQueueStatusResponse second = matchmakingService.enqueue(user.getId(), GameMode.TURN_BASED);

        assertThat(first.ticketId()).isEqualTo(second.ticketId());
        assertThat(second.gameMode()).isEqualTo(GameMode.TURN_BASED);
        assertThat(queueTicketRepository.findByStatusOrderByQueuedAtAsc(RankedQueueTicketStatus.QUEUED)).hasSize(1);
    }

    @Test
    void enqueue_rejectsNonTurnBasedMode() {
        AppUser user = createUser("queue-sim", "queue-sim@example.com");

        assertThatThrownBy(() -> matchmakingService.enqueue(user.getId(), GameMode.SIMULTANEOUS))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void status_expandsWindowBy100Every10Seconds() {
        AppUser user = createUser("queue-window", "queue-window@example.com");
        ensureProfileElo(user.getId(), 1200);

        MatchmakingDtos.PublicQueueStatusResponse queued = matchmakingService.enqueue(user.getId(), GameMode.TURN_BASED);
        RankedQueueTicket ticket = queueTicketRepository.findById(queued.ticketId()).orElseThrow();
        ticket.setQueuedAt(Instant.now().minusSeconds(25));
        queueTicketRepository.save(ticket);

        MatchmakingDtos.PublicQueueStatusResponse status = matchmakingService.getStatus(user.getId());

        assertThat(status.searchMinElo()).isEqualTo(900);
        assertThat(status.searchMaxElo()).isEqualTo(1500);
        assertThat(status.waitedSeconds()).isGreaterThanOrEqualTo(25);
    }

    @Test
    void leaveQueue_marksCurrentTicketAsCancelled() {
        AppUser user = createUser("queue-leave", "queue-leave@example.com");
        MatchmakingDtos.PublicQueueStatusResponse queued = matchmakingService.enqueue(user.getId(), GameMode.TURN_BASED);

        matchmakingService.leaveQueue(user.getId());

        RankedQueueTicket ticket = queueTicketRepository.findById(queued.ticketId()).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(RankedQueueTicketStatus.CANCELLED);
        assertThat(queueTicketRepository.findByUserIdAndStatus(user.getId(), RankedQueueTicketStatus.QUEUED)).isEmpty();
    }

    @Test
    void getStatus_returnsPendingConfirmationWhenUserHasBeenPaired() {
        AppUser user1 = createUser("queue-match-1", "queue-match-1@example.com");
        AppUser user2 = createUser("queue-match-2", "queue-match-2@example.com");
        AppUser user3 = createUser("queue-match-3", "queue-match-3@example.com");
        ensureProfileElo(user1.getId(), 1200);
        ensureProfileElo(user2.getId(), 1210);
        ensureProfileElo(user3.getId(), 1220);

        matchmakingService.enqueue(user1.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user2.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user3.getId(), GameMode.TURN_BASED);
        matchmakingService.processQueueCycle();

        MatchmakingDtos.PublicQueueStatusResponse status = matchmakingService.getStatus(user1.getId());
        assertThat(status.queued()).isTrue();
        assertThat(status.status()).isEqualTo(RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION);
        assertThat(status.confirmationDeadlineAt()).isNotNull();
        assertThat(status.confirmed()).isFalse();
        assertThat(status.matchedRoomCode()).isNull();
    }

    @Test
    void confirmMatch_createsRankedRoomWhenEveryPlayerConfirms() {
        AppUser user1 = createUser("queue-confirm-1", "queue-confirm-1@example.com");
        AppUser user2 = createUser("queue-confirm-2", "queue-confirm-2@example.com");
        AppUser user3 = createUser("queue-confirm-3", "queue-confirm-3@example.com");
        ensureProfileElo(user1.getId(), 1200);
        ensureProfileElo(user2.getId(), 1210);
        ensureProfileElo(user3.getId(), 1220);

        matchmakingService.enqueue(user1.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user2.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user3.getId(), GameMode.TURN_BASED);
        matchmakingService.processQueueCycle();

        matchmakingService.confirmMatch(user1.getId());
        matchmakingService.confirmMatch(user2.getId());
        MatchmakingDtos.PublicQueueStatusResponse finalStatus = matchmakingService.confirmMatch(user3.getId());

        assertThat(finalStatus.status()).isEqualTo(RankedQueueTicketStatus.MATCHED);
        assertThat(finalStatus.matchedRoomCode()).isNotBlank();
        assertThat(finalStatus.matchedPlayerId()).isNotNull();
        assertThat(finalStatus.confirmationDeadlineAt()).isNotNull();
        assertThat(rankedMatchRepository.findByRoomCode(finalStatus.matchedRoomCode()))
            .hasValueSatisfying(match -> assertThat(match.getStatus()).isEqualTo(RankedMatchStatus.IN_PROGRESS));
    }

    @Test
    void processQueueCycle_expiresPendingConfirmationWhenDeadlinePasses() {
        AppUser user1 = createUser("queue-expire-1", "queue-expire-1@example.com");
        AppUser user2 = createUser("queue-expire-2", "queue-expire-2@example.com");
        AppUser user3 = createUser("queue-expire-3", "queue-expire-3@example.com");
        ensureProfileElo(user1.getId(), 1200);
        ensureProfileElo(user2.getId(), 1210);
        ensureProfileElo(user3.getId(), 1220);

        matchmakingService.enqueue(user1.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user2.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user3.getId(), GameMode.TURN_BASED);
        matchmakingService.processQueueCycle();

        var pendingTickets = queueTicketRepository
                .findByStatusOrderByQueuedAtAsc(RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION);
        assertThat(pendingTickets).hasSize(3);
        pendingTickets.forEach(ticket -> ticket.setConfirmationDeadlineAt(Instant.now().minusSeconds(1)));
        queueTicketRepository.saveAll(pendingTickets);

        matchmakingService.processQueueCycle();

        MatchmakingDtos.PublicQueueStatusResponse status = matchmakingService.getStatus(user1.getId());
        assertThat(status.queued()).isFalse();
        assertThat(status.status()).isEqualTo(RankedQueueTicketStatus.EXPIRED);
        assertThat(status.matchedRoomCode()).isNull();
    }

    @Test
    void processQueueCycle_requeuesConfirmedPlayersWhenConfirmationExpires() {
        AppUser user1 = createUser("queue-requeue-1", "queue-requeue-1@example.com");
        AppUser user2 = createUser("queue-requeue-2", "queue-requeue-2@example.com");
        AppUser user3 = createUser("queue-requeue-3", "queue-requeue-3@example.com");
        ensureProfileElo(user1.getId(), 1200);
        ensureProfileElo(user2.getId(), 1210);
        ensureProfileElo(user3.getId(), 1220);

        matchmakingService.enqueue(user1.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user2.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user3.getId(), GameMode.TURN_BASED);
        matchmakingService.processQueueCycle();

        matchmakingService.confirmMatch(user1.getId());

        var pendingTickets = queueTicketRepository
                .findByStatusOrderByQueuedAtAsc(RankedQueueTicketStatus.MATCH_PENDING_CONFIRMATION);
        assertThat(pendingTickets).hasSize(3);
        Long rankedMatchId = pendingTickets.get(0).getRankedMatchId();
        pendingTickets.forEach(ticket -> ticket.setConfirmationDeadlineAt(Instant.now().minusSeconds(1)));
        queueTicketRepository.saveAll(pendingTickets);

        matchmakingService.processQueueCycle();

        MatchmakingDtos.PublicQueueStatusResponse confirmedPlayerStatus = matchmakingService.getStatus(user1.getId());
        MatchmakingDtos.PublicQueueStatusResponse unconfirmedPlayerStatus = matchmakingService.getStatus(user2.getId());

        assertThat(confirmedPlayerStatus.queued()).isTrue();
        assertThat(confirmedPlayerStatus.status()).isEqualTo(RankedQueueTicketStatus.QUEUED);
        assertThat(confirmedPlayerStatus.confirmed()).isFalse();
        assertThat(confirmedPlayerStatus.confirmationDeadlineAt()).isNull();

        assertThat(unconfirmedPlayerStatus.queued()).isFalse();
        assertThat(unconfirmedPlayerStatus.status()).isEqualTo(RankedQueueTicketStatus.EXPIRED);

        assertThat(rankedMatchRepository.findById(rankedMatchId))
                .hasValueSatisfying(match -> assertThat(match.getStatus()).isEqualTo(RankedMatchStatus.CANCELLED));
    }

    private AppUser createUser(String username, String email) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("test-hash");
        return userRepository.save(user);
    }

    private void ensureProfileElo(Long userId, int elo) {
        var profile = profileRepository.findById(userId).orElseGet(() -> {
            var created = new com.example.demo.account.model.UserRankProfile();
            created.setUserId(userId);
            created.setElo(elo);
            created.setRankedGamesPlayed(0);
            created.setProvisionalMatchesRemaining(
                    com.example.demo.account.model.UserRankProfile.DEFAULT_PROVISIONAL_MATCHES);
            return created;
        });
        profile.setElo(elo);
        profileRepository.save(profile);
    }
}
