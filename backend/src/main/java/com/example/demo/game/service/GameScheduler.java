package com.example.demo.game.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.account.service.RankedMatchmakingService;
import com.example.demo.game.model.GamePhase;
import com.example.demo.game.model.GameRoom;
import com.example.demo.game.repository.GameRoomRepository;

@Component
public class GameScheduler {

    private static final Logger log = LoggerFactory.getLogger(GameScheduler.class);

    private final GameRoomRepository roomRepository;
    private final GameService gameService;
    private final GameDataIntegrityGuard dataIntegrityGuard;
    private final RankedMatchmakingService rankedMatchmakingService;

    public GameScheduler(GameRoomRepository roomRepository, GameService gameService,
            GameDataIntegrityGuard dataIntegrityGuard,
            RankedMatchmakingService rankedMatchmakingService) {
        this.roomRepository = roomRepository;
        this.gameService = gameService;
        this.dataIntegrityGuard = dataIntegrityGuard;
        this.rankedMatchmakingService = rankedMatchmakingService;
    }

    @Scheduled(fixedRate = 2000)
    public void advanceAndBroadcast() {
        if (!dataIntegrityGuard.isStartupCheckCompleted()) {
            return;
        }

        List<GameRoom> activeRooms;
        try {
            activeRooms = roomRepository.findAll().stream()
                    .filter(room -> room.getPhase() != GamePhase.FINISHED && room.getPhase() != GamePhase.WAITING)
                    .toList();
        } catch (Exception e) {
            log.warn("Could not load active rooms during scheduler cycle: {}", e.getMessage());
            return;
        }

        for (GameRoom room : activeRooms) {
            try {
                boolean changed = gameService.advanceRoomIfNeeded(room.getCode());
                if (changed) {
                    gameService.broadcastState(room.getCode());
                }
            } catch (Exception ignored) {
                // Room might have been cleaned up
            }
        }
    }

    @Scheduled(fixedRate = 30000)
    public void cleanupRooms() {
        if (!dataIntegrityGuard.isStartupCheckCompleted()) {
            return;
        }

        try {
            gameService.cleanupFinishedRooms();
        } catch (Exception e) {
            log.warn("Could not clean finished rooms during scheduler cycle: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 1000)
    public void processRankedQueue() {
        if (!dataIntegrityGuard.isStartupCheckCompleted()) {
            return;
        }

        try {
            rankedMatchmakingService.processQueueCycle();
        } catch (Exception e) {
            log.warn("Could not process ranked queue during scheduler cycle: {}", e.getMessage());
        }
    }
}
