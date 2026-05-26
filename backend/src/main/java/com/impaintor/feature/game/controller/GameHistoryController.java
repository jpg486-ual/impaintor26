package com.impaintor.feature.game.controller;

import com.impaintor.feature.auth.config.AppUserDetails;
import com.impaintor.feature.game.dto.GameDetailResponse;
import com.impaintor.feature.game.dto.GameHistoryEntryResponse;
import com.impaintor.feature.game.dto.GamePlayerDetailResponse;
import com.impaintor.feature.game.model.GamePlayerRecord;
import com.impaintor.feature.game.model.GameRecord;
import com.impaintor.feature.game.repository.GamePlayerRecordRepository;
import com.impaintor.feature.game.repository.GameRecordRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
public class GameHistoryController {

    private final GamePlayerRecordRepository gamePlayerRecordRepository;
    private final GameRecordRepository gameRecordRepository;

    public GameHistoryController(GamePlayerRecordRepository gamePlayerRecordRepository,
                                 GameRecordRepository gameRecordRepository) {
        this.gamePlayerRecordRepository = gamePlayerRecordRepository;
        this.gameRecordRepository = gameRecordRepository;
    }

    @GetMapping
    public ResponseEntity<Page<GameHistoryEntryResponse>> history(
            @AuthenticationPrincipal AppUserDetails currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<GamePlayerRecord> records = gamePlayerRecordRepository.findByUser_Id(currentUser.getId(), pageable);

        Page<GameHistoryEntryResponse> dtoPage = records.map(r -> new GameHistoryEntryResponse(
                r.getGameRecord().getId(),
                r.getGameRecord().getPlayedAt(),
                r.isImpostor() ? "IMPOSTOR" : "PAINTER",
                r.isWinner(),
                r.getEloChange(),
                r.getGameRecord().getRoomCode()
        ));

        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GameDetailResponse> detail(
            @AuthenticationPrincipal AppUserDetails currentUser,
            @PathVariable Long id
    ) {
        GameRecord gameRecord = gameRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Game not found: " + id));

        List<GamePlayerRecord> playerRecords = gamePlayerRecordRepository.findByGameRecord_Id(id);
        boolean currentUserPlayed = playerRecords.stream()
                .anyMatch(record -> record.getUser() != null && currentUser.getId().equals(record.getUser().getId()));

        if (!currentUserPlayed) {
            throw new ResponseStatusException(FORBIDDEN, "You are not allowed to access this game");
        }

        List<GamePlayerDetailResponse> players = playerRecords.stream()
                .map(record -> new GamePlayerDetailResponse(
                        record.getUser().getId(),
                        record.getUser().getUsername(),
                        record.isImpostor(),
                        record.isWinner(),
                        record.getEloChange()
                ))
                .collect(Collectors.toList());

        GameDetailResponse response = new GameDetailResponse(
                gameRecord.getId(),
                gameRecord.getRoomCode(),
                gameRecord.getMode() != null ? gameRecord.getMode().name() : null,
                gameRecord.getSecretWord(),
                gameRecord.getWinningSide() != null ? gameRecord.getWinningSide().name() : null,
                gameRecord.getEndCondition() != null ? gameRecord.getEndCondition().name() : null,
                gameRecord.getPlayedAt(),
                gameRecord.getRounds(),
                players
        );

        return ResponseEntity.ok(response);
    }
}