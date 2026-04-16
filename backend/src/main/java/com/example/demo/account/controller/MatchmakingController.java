package com.example.demo.account.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.account.dto.MatchmakingDtos;
import com.example.demo.account.service.AuthService;
import com.example.demo.account.service.RankedMatchmakingService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/matchmaking/public")
public class MatchmakingController {

    private final RankedMatchmakingService rankedMatchmakingService;
    private final AuthService authService;

    public MatchmakingController(RankedMatchmakingService rankedMatchmakingService, AuthService authService) {
        this.rankedMatchmakingService = rankedMatchmakingService;
        this.authService = authService;
    }

    @PostMapping("/join")
    public ResponseEntity<MatchmakingDtos.PublicQueueStatusResponse> joinQueue(
            @Valid @RequestBody MatchmakingDtos.JoinPublicQueueRequest request) {
        long userId = authService.requireAuthenticatedUserId();
        MatchmakingDtos.PublicQueueStatusResponse response = rankedMatchmakingService
                .enqueue(userId, request.gameMode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveQueue() {
        long userId = authService.requireAuthenticatedUserId();
        rankedMatchmakingService.leaveQueue(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<MatchmakingDtos.PublicQueueStatusResponse> confirmQueueMatch() {
        long userId = authService.requireAuthenticatedUserId();
        MatchmakingDtos.PublicQueueStatusResponse response = rankedMatchmakingService.confirmMatch(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<MatchmakingDtos.PublicQueueStatusResponse> queueStatus() {
        long userId = authService.requireAuthenticatedUserId();
        MatchmakingDtos.PublicQueueStatusResponse response = rankedMatchmakingService.getStatus(userId);
        return ResponseEntity.ok(response);
    }
}
