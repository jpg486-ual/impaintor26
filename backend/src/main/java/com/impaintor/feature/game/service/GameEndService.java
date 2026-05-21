package com.impaintor.feature.game.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

import com.impaintor.feature.game.models.GamePlayerRecord;
import com.impaintor.feature.game.models.GameRecord;
import com.impaintor.feature.game.models.GameState;
import com.impaintor.feature.game.repository.GamePlayerRecordRepository;
import com.impaintor.feature.game.repository.GameRecordRepository;
import com.impaintor.feature.room.models.Room;
import com.impaintor.feature.room.repository.RoomRepository;
import com.impaintor.feature.user.models.User;
import com.impaintor.feature.user.repository.UserRepository;

@Service
public class GameEndService {

    private final GameRecordRepository gameRecordRepository;
    private final GamePlayerRecordRepository gamePlayerRecordRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public GameEndService(GameRecordRepository gameRecordRepository, 
                          GamePlayerRecordRepository gamePlayerRecordRepository,
                          RoomRepository roomRepository,
                          UserRepository userRepository) {
        this.gameRecordRepository = gameRecordRepository;
        this.gamePlayerRecordRepository = gamePlayerRecordRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void handleGameEnd(Room room, GameState gameState, Room.WinningSide winningSide, Room.EndCondition endCondition) {
        
        // 1. Crear GameRecord
        GameRecord gameRecord = GameRecord.builder()
                .roomCode(room.getRoomCode())
                .mode(room.getMode())
                .secretWord(room.getSecretWord())
                .winningSide(winningSide)
                .endCondition(endCondition)
                .playedAt(LocalDateTime.now())
                .build();
        
        gameRecord = gameRecordRepository.save(gameRecord);

        // 2. Crear GamePlayerRecords y actualizar Users
        List<User> players = room.getPlayersNames();
        for (User user : players) {
            boolean isImpostor = gameState.getImpostorId() != null && gameState.getImpostorId().equals(user.getId());
            boolean isWinner = false;
            
            if (winningSide == Room.WinningSide.IMPAINTOR && isImpostor) {
                isWinner = true;
            } else if (winningSide == Room.WinningSide.PAINTOR && !isImpostor) {
                isWinner = true;
            }

            // Crear record de jugador
            GamePlayerRecord playerRecord = GamePlayerRecord.builder()
                    .gameRecord(gameRecord)
                    .user(user)
                    .isImpostor(isImpostor)
                    .isWinner(isWinner)
                    .eloChange(0) // No ELO calculation yet
                    .build();
            gamePlayerRecordRepository.save(playerRecord);

            // Actualizar estadísticas
            user.setGamesPlayed((user.getGamesPlayed() == null ? 0 : user.getGamesPlayed()) + 1);
            if (isWinner) {
                user.setGamesWon((user.getGamesWon() == null ? 0 : user.getGamesWon()) + 1);
            }
            userRepository.save(user);
        }

        // 3. Limpiar estado de la sala
        room.setGameState(Room.GameState.WAITING);
        room.setSecretWord(null);
        room.setHintWord(null);
        room.setWinningSide(null);
        room.setEndCondition(null);
        room.setPlayedAt(null);
        room.setRounds(null);
        room.setImpostorTries(null);

        // Si no es CUSTOM, vaciamos la lista de jugadores para que la sala quede libre
        if (room.getMode() != Room.Mode.CUSTOM) {
            room.getPlayersNames().clear();
        }

        roomRepository.save(room);
    }
}
