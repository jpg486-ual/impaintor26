package com.example.demo.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.account.dto.MatchmakingDtos;
import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.RankedQueueTicket;
import com.example.demo.account.model.RankedQueueTicketStatus;
import com.example.demo.account.repository.AppUserRepository;
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
    void enqueue_isIdempotentForQueuedUser() {
        AppUser user = createUser("queue-repeat", "queue-repeat@example.com");

        MatchmakingDtos.PublicQueueStatusResponse first = matchmakingService.enqueue(user.getId(), GameMode.TURN_BASED);
        MatchmakingDtos.PublicQueueStatusResponse second = matchmakingService.enqueue(user.getId(), GameMode.SIMULTANEOUS);

        assertThat(first.ticketId()).isEqualTo(second.ticketId());
        assertThat(second.gameMode()).isEqualTo(GameMode.SIMULTANEOUS);
        assertThat(queueTicketRepository.findByStatusOrderByQueuedAtAsc(RankedQueueTicketStatus.QUEUED)).hasSize(1);
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
