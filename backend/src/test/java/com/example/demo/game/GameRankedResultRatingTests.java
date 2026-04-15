package com.example.demo.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.RankedMatch;
import com.example.demo.account.model.RankedMatchStatus;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.repository.EloRatingTransactionRepository;
import com.example.demo.account.repository.RankedMatchRepository;
import com.example.demo.account.repository.UserRankProfileRepository;
import com.example.demo.game.model.GameMode;
import com.example.demo.game.model.GamePhase;
import com.example.demo.game.repository.GamePlayerRepository;
import com.example.demo.game.repository.GameRoomRepository;
import com.example.demo.game.service.GameService;

@SpringBootTest
@Transactional
class GameRankedResultRatingTests {

    @Autowired
    private GameService gameService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RankedMatchRepository rankedMatchRepository;

    @Autowired
    private GameRoomRepository roomRepository;

    @Autowired
    private GamePlayerRepository playerRepository;

    @Autowired
    private UserRankProfileRepository profileRepository;

    @Autowired
    private EloRatingTransactionRepository transactionRepository;

    @Test
    void finishingRankedRoom_updatesRankedMatchAndAppliesEloTransactions() {
        AppUser user1 = createUser("ranked-final-1", "ranked-final-1@example.com");
        AppUser user2 = createUser("ranked-final-2", "ranked-final-2@example.com");
        AppUser user3 = createUser("ranked-final-3", "ranked-final-3@example.com");

        RankedMatch rankedMatch = new RankedMatch();
        rankedMatch.setStatus(RankedMatchStatus.IN_PROGRESS);
        rankedMatch.setStartedAt(Instant.now());
        rankedMatch = rankedMatchRepository.save(rankedMatch);

        String roomCode = gameService.createRankedRoom(
                rankedMatch.getId(),
                GameMode.TURN_BASED,
                List.of(
                        new GameService.RankedPlayerSeed(user1.getId(), "RankedFinalOne"),
                        new GameService.RankedPlayerSeed(user2.getId(), "RankedFinalTwo"),
                        new GameService.RankedPlayerSeed(user3.getId(), "RankedFinalThree")));

        var players = playerRepository.findByRoomCodeOrderByJoinedAtAsc(roomCode);
        assertThat(players).hasSize(3);
        players.get(0).setScore(5);
        players.get(1).setScore(2);
        players.get(2).setScore(1);
        playerRepository.saveAll(players);

        var room = roomRepository.findByCode(roomCode).orElseThrow();
        room.setCurrentRound(1);
        room.setMaxRounds(1);
        room.setPhase(GamePhase.ROUND_RESULT);
        room.setPhaseEndsAt(Instant.now().minusSeconds(1));
        roomRepository.save(room);

        boolean changed = gameService.advanceRoomIfNeeded(roomCode);
        assertThat(changed).isTrue();

        var finishedRoom = roomRepository.findByCode(roomCode).orElseThrow();
        assertThat(finishedRoom.getPhase()).isEqualTo(GamePhase.FINISHED);

        var finishedMatch = rankedMatchRepository.findById(rankedMatch.getId()).orElseThrow();
        assertThat(finishedMatch.getStatus()).isEqualTo(RankedMatchStatus.FINISHED);
        assertThat(finishedMatch.getFinishedAt()).isNotNull();

        var transactions = transactionRepository.findByRankedMatchIdOrderByCreatedAtAsc(rankedMatch.getId());
        assertThat(transactions).hasSize(3);

        int elo1 = profileRepository.findById(user1.getId()).orElseThrow().getElo();
        int elo2 = profileRepository.findById(user2.getId()).orElseThrow().getElo();
        int elo3 = profileRepository.findById(user3.getId()).orElseThrow().getElo();
        assertThat(elo1).isGreaterThan(1200);
        assertThat(elo2).isLessThan(1200);
        assertThat(elo3).isLessThan(1200);
    }

    private AppUser createUser(String username, String email) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("test-hash");
        return userRepository.save(user);
    }
}
