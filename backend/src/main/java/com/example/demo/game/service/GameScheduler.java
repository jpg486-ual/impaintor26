package com.example.demo.game.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.game.model.GamePhase;
import com.example.demo.game.model.GameRoom;
import com.example.demo.game.repository.GameRoomRepository;

@Component
public class GameScheduler {

    private final GameRoomRepository roomRepository;
    private final GameService gameService;
    private final SimpMessagingTemplate messaging;

    public GameScheduler(GameRoomRepository roomRepository, GameService gameService,
            SimpMessagingTemplate messaging) {
        this.roomRepository = roomRepository;
        this.gameService = gameService;
        this.messaging = messaging;
    }

    @Scheduled(fixedRate = 2000)
    public void advanceAndBroadcast() {
        List<GameRoom> activeRooms = roomRepository.findAll().stream()
                .filter(room -> room.getPhase() != GamePhase.FINISHED && room.getPhase() != GamePhase.WAITING)
                .toList();

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
        gameService.cleanupFinishedRooms();
    }
}
