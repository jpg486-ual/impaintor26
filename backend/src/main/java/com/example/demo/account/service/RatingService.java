package com.example.demo.account.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.EloRatingTransaction;
import com.example.demo.account.model.UserRankProfile;
import com.example.demo.account.model.UserStatus;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.repository.EloRatingTransactionRepository;
import com.example.demo.account.repository.UserRankProfileRepository;

@Service
public class RatingService {

    public static final int PROVISIONAL_K_FACTOR = 40;
    public static final int STABLE_K_FACTOR = 24;
    private static final int MIN_ELO = 100;

    private final AppUserRepository userRepository;
    private final UserRankProfileRepository profileRepository;
    private final EloRatingTransactionRepository ratingTransactionRepository;

    public RatingService(
            AppUserRepository userRepository,
            UserRankProfileRepository profileRepository,
            EloRatingTransactionRepository ratingTransactionRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.ratingTransactionRepository = ratingTransactionRepository;
    }

    @Transactional
    public UserRankProfile getOrCreateProfile(long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no disponible para ranked");
        }

        return profileRepository.findById(userId).orElseGet(() -> {
            UserRankProfile profile = new UserRankProfile();
            profile.setUserId(userId);
            profile.setElo(UserRankProfile.DEFAULT_ELO);
            profile.setRankedGamesPlayed(0);
            profile.setProvisionalMatchesRemaining(UserRankProfile.DEFAULT_PROVISIONAL_MATCHES);
            return profileRepository.save(profile);
        });
    }

    @Transactional
    public EloRatingTransaction recordRatingUpdate(long rankedMatchId, long userId, int nextElo, int kFactor) {
        UserRankProfile profile = getOrCreateProfile(userId);
        int previousElo = profile.getElo();
        int delta = nextElo - previousElo;

        profile.setElo(nextElo);
        profile.setRankedGamesPlayed(profile.getRankedGamesPlayed() + 1);
        if (profile.getProvisionalMatchesRemaining() > 0) {
            profile.setProvisionalMatchesRemaining(profile.getProvisionalMatchesRemaining() - 1);
        }
        profileRepository.save(profile);

        EloRatingTransaction transaction = new EloRatingTransaction();
        transaction.setRankedMatchId(rankedMatchId);
        transaction.setUserId(userId);
        transaction.setEloBefore(previousElo);
        transaction.setEloAfter(nextElo);
        transaction.setDelta(delta);
        transaction.setKFactor(kFactor);
        return ratingTransactionRepository.save(transaction);
    }

    public int resolveKFactor(UserRankProfile profile) {
        return profile.getProvisionalMatchesRemaining() > 0 ? PROVISIONAL_K_FACTOR : STABLE_K_FACTOR;
    }

    public double expectedScore(int playerElo, double opponentAverageElo) {
        return 1d / (1d + Math.pow(10d, (opponentAverageElo - playerElo) / 400d));
    }

    public int computeNextElo(int playerElo, double expectedScore, double actualScore, int kFactor) {
        int next = (int) Math.round(playerElo + (kFactor * (actualScore - expectedScore)));
        return Math.max(MIN_ELO, next);
    }
}
