package com.example.demo.account.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.account.dto.MatchmakingDtos;
import com.example.demo.account.service.RankedMatchmakingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@Validated
@RequestMapping("/api/matchmaking/public")
public class MatchmakingController {

    private final RankedMatchmakingService rankedMatchmakingService;

    public MatchmakingController(RankedMatchmakingService rankedMatchmakingService) {
        this.rankedMatchmakingService = rankedMatchmakingService;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<MatchmakingDtos.PublicPlayerProfileResponse> bootstrapPublicPlayer(
            @Valid @RequestBody MatchmakingDtos.BootstrapPublicPlayerRequest request) {
        MatchmakingDtos.PublicPlayerProfileResponse response = rankedMatchmakingService
                .bootstrapPublicPlayer(request.username());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/join")
    public ResponseEntity<MatchmakingDtos.PublicQueueStatusResponse> joinQueue(
            @Valid @RequestBody MatchmakingDtos.JoinPublicQueueRequest request) {
        MatchmakingDtos.PublicQueueStatusResponse response = rankedMatchmakingService
                .enqueue(request.userId(), request.gameMode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveQueue(@Valid @RequestBody MatchmakingDtos.LeavePublicQueueRequest request) {
        rankedMatchmakingService.leaveQueue(request.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<MatchmakingDtos.PublicQueueStatusResponse> confirmQueueMatch(
            @Valid @RequestBody MatchmakingDtos.ConfirmPublicQueueMatchRequest request) {
        MatchmakingDtos.PublicQueueStatusResponse response = rankedMatchmakingService.confirmMatch(request.userId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<MatchmakingDtos.PublicQueueStatusResponse> queueStatus(
            @RequestParam @Min(1) long userId) {
        MatchmakingDtos.PublicQueueStatusResponse response = rankedMatchmakingService.getStatus(userId);
        return ResponseEntity.ok(response);
    }
}
