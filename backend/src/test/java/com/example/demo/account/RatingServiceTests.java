package com.example.demo.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.UserRankProfile;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.repository.EloRatingTransactionRepository;
import com.example.demo.account.repository.UserRankProfileRepository;
import com.example.demo.account.service.RatingService;

@SpringBootTest
@Transactional
class RatingServiceTests {

    @Autowired
    private RatingService ratingService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserRankProfileRepository profileRepository;

    @Autowired
    private EloRatingTransactionRepository transactionRepository;

    @Test
    void getOrCreateProfile_createsDefaultProfileForExistingUser() {
        AppUser user = createUser("profile-user", "profile@example.com");

        UserRankProfile profile = ratingService.getOrCreateProfile(user.getId());

        assertThat(profile.getUserId()).isEqualTo(user.getId());
        assertThat(profile.getElo()).isEqualTo(UserRankProfile.DEFAULT_ELO);
        assertThat(profile.getRankedGamesPlayed()).isZero();
        assertThat(profile.getProvisionalMatchesRemaining()).isEqualTo(UserRankProfile.DEFAULT_PROVISIONAL_MATCHES);
    }

    @Test
    void recordRatingUpdate_updatesProfileAndPersistsTransaction() {
        AppUser user = createUser("rating-user", "rating@example.com");
        ratingService.getOrCreateProfile(user.getId());

        var transaction = ratingService.recordRatingUpdate(123L, user.getId(), 1248, 24);
        UserRankProfile updatedProfile = profileRepository.findById(user.getId()).orElseThrow();

        assertThat(updatedProfile.getElo()).isEqualTo(1248);
        assertThat(updatedProfile.getRankedGamesPlayed()).isEqualTo(1);
        assertThat(updatedProfile.getProvisionalMatchesRemaining())
                .isEqualTo(UserRankProfile.DEFAULT_PROVISIONAL_MATCHES - 1);

        assertThat(transaction.getRankedMatchId()).isEqualTo(123L);
        assertThat(transaction.getUserId()).isEqualTo(user.getId());
        assertThat(transaction.getEloBefore()).isEqualTo(UserRankProfile.DEFAULT_ELO);
        assertThat(transaction.getEloAfter()).isEqualTo(1248);
        assertThat(transaction.getDelta()).isEqualTo(48);
        assertThat(transaction.getKFactor()).isEqualTo(24);
        assertThat(transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).hasSize(1);
    }

    private AppUser createUser(String username, String email) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("test-hash");
        return userRepository.save(user);
    }
}
