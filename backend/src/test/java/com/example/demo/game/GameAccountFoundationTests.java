package com.example.demo.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.demo.game.dto.GameRequests;
import com.example.demo.game.dto.GameResponses;
import com.example.demo.game.model.GameMode;
import com.example.demo.game.model.GameRoomType;
import com.example.demo.game.repository.GamePlayerRepository;
import com.example.demo.game.repository.GameRoomRepository;
import com.example.demo.game.service.GameService;

@SpringBootTest
class GameAccountFoundationTests {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRoomRepository roomRepository;

    @Autowired
    private GamePlayerRepository playerRepository;

    @Test
    void createRoom_defaultsToPrivateUnrankedAndGuestHostPlayer() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostFoundation", 60, 25, 5, List.of("animales"),
                        GameMode.SIMULTANEOUS));

        var room = roomRepository.findByCode(host.roomCode()).orElseThrow();
        assertThat(room.getRoomType()).isEqualTo(GameRoomType.PRIVATE_UNRANKED);
        assertThat(room.getRankedMatchId()).isNull();

        var player = playerRepository.findByIdAndRoomCode(host.playerId(), host.roomCode()).orElseThrow();
        assertThat(player.getUserId()).isNull();
    }

    @Test
    void joinRoom_keepsGuestIdentityWhenNoAccountIsAttached() {
        GameResponses.RoomJoinResponse host = gameService.createRoom(
                new GameRequests.CreateRoomRequest("HostGuestJoin", 60, 25, 5, List.of("comida"), GameMode.TURN_BASED));

        GameResponses.RoomJoinResponse guest = gameService.joinRoom(host.roomCode(),
                new GameRequests.JoinRoomRequest("GuestNoAccount"));

        var joined = playerRepository.findByIdAndRoomCode(guest.playerId(), host.roomCode()).orElseThrow();
        assertThat(joined.getUserId()).isNull();
    }
}
