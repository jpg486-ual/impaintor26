package com.impaintor.feature.game.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.impaintor.feature.game.models.GamePlayerRecord;
import com.impaintor.feature.game.models.GameRecord;
import com.impaintor.feature.game.models.GameState;
import com.impaintor.feature.game.repository.GamePlayerRecordRepository;
import com.impaintor.feature.game.repository.GameRecordRepository;
import com.impaintor.feature.room.models.Room;
import com.impaintor.feature.room.repository.RoomRepository;
import com.impaintor.feature.user.models.User;
import com.impaintor.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class GameEndServiceTest {

    @Mock
    private GameRecordRepository gameRecordRepository;
    @Mock
    private GamePlayerRecordRepository gamePlayerRecordRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GameEndService gameEndService;

    private Room room;
    private GameState gameState;
    private User player1;
    private User player2;

    @BeforeEach
    void setUp() {
        player1 = new User();
        player1.setId(1L);
        player1.setGamesPlayed(10);
        player1.setGamesWon(5);

        player2 = new User();
        player2.setId(2L);
        player2.setGamesPlayed(10);
        player2.setGamesWon(5);

        List<User> players = new ArrayList<>();
        players.add(player1);
        players.add(player2);

        room = new Room();
        room.setRoomCode("TEST12");
        room.setMode(Room.Mode.RANKED);
        room.setSecretWord("secret");
        room.setPlayersNames(players);
        room.setGameState(Room.GameState.PLAYING);
        room.setWinningSide(Room.WinningSide.IMPAINTOR);
        room.setEndCondition(Room.EndCondition.LAST_STANDING);
        room.setImpostorTries(1);
        room.setHintWord("hint");
        room.setRounds(2);

        gameState = new GameState();
        gameState.setImpostorId(1L); // player1 is impostor
        
        when(gameRecordRepository.save(any(GameRecord.class))).thenAnswer(i -> {
            GameRecord gr = i.getArgument(0);
            gr.setId(100L);
            return gr;
        });
    }

    @Test
    void testHandleGameEnd_RankedMode_ImpostorWins() {
        gameEndService.handleGameEnd(room, gameState, Room.WinningSide.IMPAINTOR, Room.EndCondition.LAST_STANDING);

        verify(gameRecordRepository, times(1)).save(any(GameRecord.class));
        verify(gamePlayerRecordRepository, times(2)).save(any(GamePlayerRecord.class));
        
        // Impostor (player1) wins
        assertEquals(11, player1.getGamesPlayed());
        assertEquals(6, player1.getGamesWon()); 

        // Paintor (player2) loses
        assertEquals(11, player2.getGamesPlayed());
        assertEquals(5, player2.getGamesWon());

        verify(userRepository, times(2)).save(any(User.class));

        // Room state cleaned
        assertEquals(Room.GameState.WAITING, room.getGameState());
        assertNull(room.getSecretWord());
        assertNull(room.getHintWord());
        assertNull(room.getWinningSide());
        assertNull(room.getEndCondition());
        assertNull(room.getPlayedAt());
        assertNull(room.getRounds());
        assertNull(room.getImpostorTries());

        // Players cleared because it is RANKED
        assertTrue(room.getPlayersNames().isEmpty());
        
        verify(roomRepository, times(1)).save(room);
    }

    @Test
    void testHandleGameEnd_CustomMode_PaintorsWin() {
        room.setMode(Room.Mode.CUSTOM);
        
        gameEndService.handleGameEnd(room, gameState, Room.WinningSide.PAINTOR, Room.EndCondition.VOTED_OUT);

        // Impostor (player1) loses
        assertEquals(11, player1.getGamesPlayed());
        assertEquals(5, player1.getGamesWon()); 

        // Paintor (player2) wins
        assertEquals(11, player2.getGamesPlayed());
        assertEquals(6, player2.getGamesWon());

        // Players NOT cleared because it is CUSTOM
        assertFalse(room.getPlayersNames().isEmpty());
        assertEquals(2, room.getPlayersNames().size());
    }

    @Test
    void testHandleGameEnd_SwiftplayMode_PlayersCleared() {
        room.setMode(Room.Mode.SWIFTPLAY);
        
        gameEndService.handleGameEnd(room, gameState, Room.WinningSide.IMPAINTOR, Room.EndCondition.LAST_STANDING);

        // Players cleared because it is SWIFTPLAY (like RANKED)
        assertTrue(room.getPlayersNames().isEmpty());
    }

    @Test
    void testHandleGameEnd_EmptyPlayersList() {
        room.getPlayersNames().clear();
        
        gameEndService.handleGameEnd(room, gameState, Room.WinningSide.IMPAINTOR, Room.EndCondition.LAST_STANDING);

        verify(gameRecordRepository, times(1)).save(any(GameRecord.class));
        verify(gamePlayerRecordRepository, never()).save(any(GamePlayerRecord.class));
        verify(userRepository, never()).save(any(User.class));
        
        assertTrue(room.getPlayersNames().isEmpty());
    }

    @Test
    void testHandleGameEnd_NoImpostorIdInGameState() {
        gameState.setImpostorId(null);
        
        gameEndService.handleGameEnd(room, gameState, Room.WinningSide.PAINTOR, Room.EndCondition.WORD_GUESSED);

        // Si no hay impostor, nadie es impostor. Si ganan los pintores, todos (al no ser impostores) ganan.
        assertEquals(11, player1.getGamesPlayed());
        assertEquals(6, player1.getGamesWon()); 
        
        assertEquals(11, player2.getGamesPlayed());
        assertEquals(6, player2.getGamesWon());
    }
}
