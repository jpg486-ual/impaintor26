package com.example.demo.game.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.game.dto.GameRequests;
import com.example.demo.game.dto.GameResponses;
import com.example.demo.game.service.GameService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/rooms")
@Validated
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameResponses.RoomJoinResponse createRoom(@Valid @RequestBody GameRequests.CreateRoomRequest request) {
        return gameService.createRoom(request);
    }

    @PostMapping("/{roomCode}/join")
    public GameResponses.RoomJoinResponse joinRoom(
            @PathVariable String roomCode,
            @Valid @RequestBody GameRequests.JoinRoomRequest request) {
        return gameService.joinRoom(roomCode, request);
    }

    @PostMapping("/{roomCode}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start(
            @PathVariable String roomCode,
            @Valid @RequestBody GameRequests.StartGameRequest request) {
        gameService.startGame(roomCode, request);
    }

    @GetMapping("/{roomCode}/state")
    public GameResponses.GameStateResponse state(@PathVariable String roomCode, @RequestParam long playerId) {
        return gameService.getState(roomCode, playerId);
    }

    @PostMapping("/{roomCode}/strokes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addStroke(@PathVariable String roomCode, @Valid @RequestBody GameRequests.AddStrokeRequest request) {
        gameService.addStroke(roomCode, request);
    }

    @PostMapping("/{roomCode}/votes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vote(@PathVariable String roomCode, @Valid @RequestBody GameRequests.VoteRequest request) {
        gameService.vote(roomCode, request);
    }
}
