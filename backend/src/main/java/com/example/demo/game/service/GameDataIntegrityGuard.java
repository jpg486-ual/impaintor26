package com.example.demo.game.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.demo.game.model.DrawingStroke;
import com.example.demo.game.model.GamePlayer;
import com.example.demo.game.model.GameRoom;
import com.example.demo.game.model.GameVote;
import com.example.demo.game.repository.DrawingStrokeRepository;
import com.example.demo.game.repository.GamePlayerRepository;
import com.example.demo.game.repository.GameRoomRepository;
import com.example.demo.game.repository.GameVoteRepository;

@Component
public class GameDataIntegrityGuard {

    private static final Logger log = LoggerFactory.getLogger(GameDataIntegrityGuard.class);

    private final GameRoomRepository roomRepository;
    private final GamePlayerRepository playerRepository;
    private final DrawingStrokeRepository strokeRepository;
    private final GameVoteRepository voteRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean startupCheckCompleted = new AtomicBoolean(false);

    public GameDataIntegrityGuard(
            GameRoomRepository roomRepository,
            GamePlayerRepository playerRepository,
            DrawingStrokeRepository strokeRepository,
            GameVoteRepository voteRepository,
            JdbcTemplate jdbcTemplate) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.strokeRepository = strokeRepository;
        this.voteRepository = voteRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyAndRecoverIfNeeded() {
        try {
            List<String> issues = new ArrayList<>();
            issues.addAll(repairKnownSchemaDrift());
            issues.addAll(findIntegrityIssues());

            if (issues.isEmpty()) {
                log.info("Database integrity check passed on startup");
                return;
            }

            log.error("Database integrity check failed on startup ({} issues). Reinitializing game data.", issues.size());
            for (String issue : issues) {
                log.error("Integrity issue: {}", issue);
            }
            resetGameData();
        } finally {
            startupCheckCompleted.set(true);
        }
    }

    public boolean isStartupCheckCompleted() {
        return startupCheckCompleted.get();
    }

    private List<String> repairKnownSchemaDrift() {
        List<String> issues = new ArrayList<>();

        ensureColumn(
                issues,
                "game_rooms",
                "game_mode",
                "ALTER TABLE game_rooms ADD COLUMN IF NOT EXISTS game_mode VARCHAR(32) NOT NULL DEFAULT 'SIMULTANEOUS'");
        ensureColumn(
                issues,
                "game_rooms",
                "active_drawer_player_id",
                "ALTER TABLE game_rooms ADD COLUMN IF NOT EXISTS active_drawer_player_id BIGINT");
        ensureColumn(
                issues,
                "game_rooms",
                "active_drawer_turn_index",
                "ALTER TABLE game_rooms ADD COLUMN IF NOT EXISTS active_drawer_turn_index INTEGER NOT NULL DEFAULT 0");
        ensureColumn(
                issues,
                "game_rooms",
                "turns_completed_in_round",
                "ALTER TABLE game_rooms ADD COLUMN IF NOT EXISTS turns_completed_in_round INTEGER NOT NULL DEFAULT 0");
        ensureColumn(
                issues,
                "game_rooms",
                "room_type",
                "ALTER TABLE game_rooms ADD COLUMN IF NOT EXISTS room_type VARCHAR(32) NOT NULL DEFAULT 'PRIVATE_UNRANKED'");
        ensureColumn(
                issues,
                "game_rooms",
                "ranked_match_id",
                "ALTER TABLE game_rooms ADD COLUMN IF NOT EXISTS ranked_match_id BIGINT");
        ensureColumn(
                issues,
                "game_players",
                "user_id",
                "ALTER TABLE game_players ADD COLUMN IF NOT EXISTS user_id BIGINT");

        return issues;
    }

    private void ensureColumn(List<String> issues, String table, String column, String addColumnSql) {
        if (canExecute("SELECT " + column + " FROM " + table + " WHERE 1 = 0")) {
            return;
        }

        issues.add("Missing schema column " + table + "." + column);
        if (!canExecute(addColumnSql)) {
            issues.add("Failed to add missing schema column " + table + "." + column);
        }
    }

    private boolean canExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> findIntegrityIssues() {
        List<String> issues = new ArrayList<>();
        try {
            List<GameRoom> rooms = roomRepository.findAll();
            List<GamePlayer> players = playerRepository.findAll();
            List<GameVote> votes = voteRepository.findAll();
            List<DrawingStroke> strokes = strokeRepository.findAll();

            Map<String, GameRoom> roomsByCode = new HashMap<>();
            for (GameRoom room : rooms) {
                if (room.getCode() != null) {
                    roomsByCode.put(room.getCode(), room);
                }
            }

            Map<Long, GamePlayer> playersById = new HashMap<>();
            for (GamePlayer player : players) {
                if (player.getId() != null) {
                    playersById.put(player.getId(), player);
                }

                GameRoom room = roomsByCode.get(player.getRoomCode());
                if (room == null) {
                    issues.add("Player " + player.getId() + " references missing room " + player.getRoomCode());
                }
            }

            for (GameVote vote : votes) {
                GameRoom room = roomsByCode.get(vote.getRoomCode());
                if (room == null) {
                    issues.add("Vote references missing room " + vote.getRoomCode());
                }

                GamePlayer voter = playersById.get(vote.getVoterPlayerId());
                if (voter == null) {
                    issues.add("Vote references missing voter " + vote.getVoterPlayerId());
                } else if (!vote.getRoomCode().equals(voter.getRoomCode())) {
                    issues.add("Vote voter " + vote.getVoterPlayerId() + " is in another room");
                }

                GamePlayer target = playersById.get(vote.getTargetPlayerId());
                if (target == null) {
                    issues.add("Vote references missing target " + vote.getTargetPlayerId());
                } else if (!vote.getRoomCode().equals(target.getRoomCode())) {
                    issues.add("Vote target " + vote.getTargetPlayerId() + " is in another room");
                }
            }

            for (DrawingStroke stroke : strokes) {
                GameRoom room = roomsByCode.get(stroke.getRoomCode());
                if (room == null) {
                    issues.add("Stroke references missing room " + stroke.getRoomCode());
                }

                GamePlayer player = playersById.get(stroke.getPlayerId());
                if (player == null) {
                    issues.add("Stroke references missing player " + stroke.getPlayerId());
                } else if (!stroke.getRoomCode().equals(player.getRoomCode())) {
                    issues.add("Stroke player " + stroke.getPlayerId() + " is in another room");
                }
            }

            for (GameRoom room : rooms) {
                if (room.getImpostorPlayerId() != null) {
                    GamePlayer impostor = playersById.get(room.getImpostorPlayerId());
                    if (impostor == null || !room.getCode().equals(impostor.getRoomCode())) {
                        issues.add("Room " + room.getCode() + " has invalid impostorPlayerId");
                    }
                }

                if (room.getActiveDrawerPlayerId() != null) {
                    GamePlayer drawer = playersById.get(room.getActiveDrawerPlayerId());
                    if (drawer == null || !room.getCode().equals(drawer.getRoomCode())) {
                        issues.add("Room " + room.getCode() + " has invalid activeDrawerPlayerId");
                    }
                }

                if (room.getCurrentRound() < 0 || room.getMaxRounds() <= 0) {
                    issues.add("Room " + room.getCode() + " has invalid round values");
                }
            }
        } catch (Exception e) {
            issues.add("Integrity check execution failed: " + e.getMessage());
        }

        return issues;
    }

    private void resetGameData() {
        safeExecute("DELETE FROM game_votes");
        safeExecute("DELETE FROM drawing_strokes");
        safeExecute("DELETE FROM game_players");
        safeExecute("DELETE FROM game_rooms");
        log.warn("All game data was reinitialized due to integrity check failure");
    }

    private void safeExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Could not execute recovery SQL [{}]: {}", sql, e.getMessage());
        }
    }
}
