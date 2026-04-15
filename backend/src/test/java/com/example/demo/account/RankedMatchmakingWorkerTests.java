package com.example.demo.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.RankedMatch;
import com.example.demo.account.model.RankedMatchStatus;
import com.example.demo.account.model.RankedQueueTicket;
import com.example.demo.account.model.RankedQueueTicketStatus;
import com.example.demo.account.model.UserRankProfile;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.repository.RankedMatchRepository;
import com.example.demo.account.repository.RankedQueueTicketRepository;
import com.example.demo.account.repository.UserRankProfileRepository;
import com.example.demo.account.service.RankedMatchmakingService;
import com.example.demo.game.model.GameMode;
import com.example.demo.game.model.GameRoomType;
import com.example.demo.game.repository.GamePlayerRepository;
import com.example.demo.game.repository.GameRoomRepository;

@SpringBootTest
@Transactional
class RankedMatchmakingWorkerTests {

    @Autowired
    private RankedMatchmakingService matchmakingService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserRankProfileRepository profileRepository;

    @Autowired
    private RankedQueueTicketRepository queueTicketRepository;

    @Autowired
    private RankedMatchRepository rankedMatchRepository;

    @Autowired
    private GameRoomRepository roomRepository;

    @Autowired
    private GamePlayerRepository playerRepository;

    @Test
    void processQueueCycle_createsRankedRoomAndStartsMatchForCompatiblePlayers() {
        AppUser user1 = createUser("rankeduser1", "rankeduser1@example.com");
        AppUser user2 = createUser("rankeduser2", "rankeduser2@example.com");
        AppUser user3 = createUser("rankeduser3", "rankeduser3@example.com");
        ensureProfileElo(user1.getId(), 1200);
        ensureProfileElo(user2.getId(), 1260);
        ensureProfileElo(user3.getId(), 1280);

        matchmakingService.enqueue(user1.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user2.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user3.getId(), GameMode.TURN_BASED);

        int matchesCreated = matchmakingService.processQueueCycle();

        assertThat(matchesCreated).isEqualTo(1);

        List<RankedQueueTicket> matchedTickets = queueTicketRepository
                .findByStatusOrderByQueuedAtAsc(RankedQueueTicketStatus.MATCHED);
        assertThat(matchedTickets).hasSize(3);
        String roomCode = matchedTickets.get(0).getMatchedRoomCode();
        assertThat(roomCode).isNotBlank();
        assertThat(matchedTickets).allMatch(ticket -> roomCode.equals(ticket.getMatchedRoomCode()));

        var room = roomRepository.findByCode(roomCode).orElseThrow();
        assertThat(room.getRoomType()).isEqualTo(GameRoomType.PUBLIC_RANKED);
        assertThat(room.getRankedMatchId()).isNotNull();
        assertThat(room.getPhase()).isNotNull();
        assertThat(room.getPhase()).isNotEqualTo(com.example.demo.game.model.GamePhase.WAITING);

        var players = playerRepository.findByRoomCodeOrderByJoinedAtAsc(roomCode);
        assertThat(players).hasSize(3);
        assertThat(players)
                .extracting(player -> player.getUserId())
                .containsExactlyInAnyOrder(user1.getId(), user2.getId(), user3.getId());

        RankedMatch rankedMatch = rankedMatchRepository.findById(room.getRankedMatchId()).orElseThrow();
        assertThat(rankedMatch.getStatus()).isEqualTo(RankedMatchStatus.IN_PROGRESS);
        assertThat(rankedMatch.getRoomCode()).isEqualTo(roomCode);
        assertThat(rankedMatch.getStartedAt()).isNotNull();
    }

    @Test
    void processQueueCycle_doesNotCreateMatchWhenPlayersAreNotMutuallyCompatible() {
        AppUser user1 = createUser("rankedincompatible1", "rankedincompatible1@example.com");
        AppUser user2 = createUser("rankedincompatible2", "rankedincompatible2@example.com");
        AppUser user3 = createUser("rankedincompatible3", "rankedincompatible3@example.com");
        ensureProfileElo(user1.getId(), 1200);
        ensureProfileElo(user2.getId(), 1600);
        ensureProfileElo(user3.getId(), 2000);

        matchmakingService.enqueue(user1.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user2.getId(), GameMode.TURN_BASED);
        matchmakingService.enqueue(user3.getId(), GameMode.TURN_BASED);

        int matchesCreated = matchmakingService.processQueueCycle();

        assertThat(matchesCreated).isZero();
        assertThat(rankedMatchRepository.findAll()).isEmpty();
        assertThat(queueTicketRepository.findByStatusOrderByQueuedAtAsc(RankedQueueTicketStatus.QUEUED)).hasSize(3);
    }

    private AppUser createUser(String username, String email) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("test-hash");
        return userRepository.save(user);
    }

    private void ensureProfileElo(Long userId, int elo) {
        UserRankProfile profile = profileRepository.findById(userId).orElseGet(() -> {
            UserRankProfile created = new UserRankProfile();
            created.setUserId(userId);
            created.setRankedGamesPlayed(0);
            created.setProvisionalMatchesRemaining(UserRankProfile.DEFAULT_PROVISIONAL_MATCHES);
            return created;
        });
        profile.setElo(elo);
        profileRepository.save(profile);
    }
}
