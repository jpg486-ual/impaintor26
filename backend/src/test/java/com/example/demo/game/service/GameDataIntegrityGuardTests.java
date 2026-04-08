package com.example.demo.game.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.game.model.DrawingStroke;
import com.example.demo.game.model.GamePlayer;
import com.example.demo.game.model.GameVote;
import com.example.demo.game.repository.DrawingStrokeRepository;
import com.example.demo.game.repository.GamePlayerRepository;
import com.example.demo.game.repository.GameRoomRepository;
import com.example.demo.game.repository.GameVoteRepository;

@ExtendWith(MockitoExtension.class)
class GameDataIntegrityGuardTests {

    @Mock
    private GameRoomRepository roomRepository;

    @Mock
    private GamePlayerRepository playerRepository;

    @Mock
    private DrawingStrokeRepository strokeRepository;

    @Mock
    private GameVoteRepository voteRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private GameDataIntegrityGuard guard;

    @Test
    void doesNotResetWhenIntegrityIsValid() {
        GamePlayer player = new GamePlayer();
        ReflectionTestUtils.setField(player, "id", 10L);
        player.setRoomCode("ABC123");
        player.setName("Player");
        player.setScore(0);

        when(roomRepository.findAll()).thenReturn(List.of(createRoom("ABC123", 1, 3, 10L, 10L)));
        when(playerRepository.findAll()).thenReturn(List.of(player));
        when(voteRepository.findAll()).thenReturn(List.of());
        when(strokeRepository.findAll()).thenReturn(List.of());

        guard.verifyAndRecoverIfNeeded();

        verify(jdbcTemplate, never()).execute("DELETE FROM game_votes");
        verify(jdbcTemplate, never()).execute("DELETE FROM drawing_strokes");
        verify(jdbcTemplate, never()).execute("DELETE FROM game_players");
        verify(jdbcTemplate, never()).execute("DELETE FROM game_rooms");
    }

    @Test
    void resetsAllGameDataWhenIntegrityFails() {
        GamePlayer orphanPlayer = new GamePlayer();
        ReflectionTestUtils.setField(orphanPlayer, "id", 1L);
        orphanPlayer.setRoomCode("MISSING");
        orphanPlayer.setName("Orphan");
        orphanPlayer.setScore(0);

        GameVote brokenVote = new GameVote();
        brokenVote.setRoomCode("MISSING");
        brokenVote.setRoundNumber(1);
        brokenVote.setVoterPlayerId(1L);
        brokenVote.setTargetPlayerId(2L);

        DrawingStroke brokenStroke = new DrawingStroke();
        brokenStroke.setRoomCode("MISSING");
        brokenStroke.setRoundNumber(1);
        brokenStroke.setPlayerId(1L);
        brokenStroke.setPathData("10:10;20:20");

        when(roomRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of(orphanPlayer));
        when(voteRepository.findAll()).thenReturn(List.of(brokenVote));
        when(strokeRepository.findAll()).thenReturn(List.of(brokenStroke));

        guard.verifyAndRecoverIfNeeded();

        verify(jdbcTemplate).execute("DELETE FROM game_votes");
        verify(jdbcTemplate).execute("DELETE FROM drawing_strokes");
        verify(jdbcTemplate).execute("DELETE FROM game_players");
        verify(jdbcTemplate).execute("DELETE FROM game_rooms");
    }

    @Test
    void resetsAllGameDataWhenCheckExecutionThrows() {
        when(roomRepository.findAll()).thenThrow(new RuntimeException("broken read"));

        guard.verifyAndRecoverIfNeeded();

        verify(jdbcTemplate).execute("DELETE FROM game_votes");
        verify(jdbcTemplate).execute("DELETE FROM drawing_strokes");
        verify(jdbcTemplate).execute("DELETE FROM game_players");
        verify(jdbcTemplate).execute("DELETE FROM game_rooms");
    }

    @Test
    void marksStartupCheckAsCompletedEvenWhenResetPathRuns() {
        when(roomRepository.findAll()).thenThrow(new RuntimeException("broken read"));

        guard.verifyAndRecoverIfNeeded();

        verify(jdbcTemplate, times(1)).execute("DELETE FROM game_votes");
        org.junit.jupiter.api.Assertions.assertTrue(guard.isStartupCheckCompleted());
    }

    @Test
    void repairsMissingTurnsCompletedColumnAndThenReinitializesData() {
        org.mockito.Mockito.doAnswer((Answer<Void>) invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT turns_completed_in_round FROM game_rooms")) {
                throw new RuntimeException("missing column");
            }
            return null;
        }).when(jdbcTemplate).execute(org.mockito.ArgumentMatchers.anyString());

        when(roomRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());
        when(voteRepository.findAll()).thenReturn(List.of());
        when(strokeRepository.findAll()).thenReturn(List.of());

        guard.verifyAndRecoverIfNeeded();

        verify(jdbcTemplate)
            .execute(
                "ALTER TABLE game_rooms ADD COLUMN IF NOT EXISTS turns_completed_in_round INTEGER NOT NULL DEFAULT 0");
        verify(jdbcTemplate).execute("DELETE FROM game_votes");
        verify(jdbcTemplate).execute("DELETE FROM drawing_strokes");
        verify(jdbcTemplate).execute("DELETE FROM game_players");
        verify(jdbcTemplate).execute("DELETE FROM game_rooms");
    }

    private com.example.demo.game.model.GameRoom createRoom(
            String code,
            int currentRound,
            int maxRounds,
            Long impostorPlayerId,
            Long activeDrawerPlayerId) {
        com.example.demo.game.model.GameRoom room = new com.example.demo.game.model.GameRoom();
        room.setCode(code);
        room.setCurrentRound(currentRound);
        room.setMaxRounds(maxRounds);
        room.setImpostorPlayerId(impostorPlayerId);
        room.setActiveDrawerPlayerId(activeDrawerPlayerId);
        room.setRoundDurationSeconds(60);
        room.setVotingDurationSeconds(20);
        room.setHostName("Host");
        room.setThemesCsv("animales");
        room.setActiveDrawerTurnIndex(0);
        room.setTurnsCompletedInRound(0);
        room.setPhase(com.example.demo.game.model.GamePhase.DRAWING);
        room.setGameMode(com.example.demo.game.model.GameMode.TURN_BASED);
        return room;
    }
}