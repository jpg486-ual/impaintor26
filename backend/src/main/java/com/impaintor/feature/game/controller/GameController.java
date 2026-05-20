package com.impaintor.feature.game.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.impaintor.feature.game.model.GameState;
import com.impaintor.feature.game.service.GameService;
import com.impaintor.feature.room.models.Room;
import com.impaintor.feature.room.repository.RoomRepository;

/**
 * Endpoints del feature de juego
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    private final RoomRepository roomRepository;
    private final GameService gameService;

    public GameController(RoomRepository roomRepository, GameService gameService) {
        this.roomRepository = roomRepository;
        this.gameService = gameService;
    }

    @PostMapping("/rooms/{code}/start")
    public ResponseEntity<?> startGame(@PathVariable String code) {
        Optional<Room> oRoom = roomRepository.findById(code);
        if (oRoom.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Room room = oRoom.get();
        if (room.getGameState() != Room.GameState.WAITING) {
            return ResponseEntity.badRequest().body("La sala no esta en espera.");
        }

        String error = validateStartRequirements(room);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        try {
            GameState gameState = gameService.initializeGame(code);
            return ResponseEntity.ok(gameState);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    private String validateStartRequirements(Room room) {
        int numJugadores = room.getPlayersNames().size();

        if (room.getMode() == Room.Mode.RANKED && numJugadores != 5) {
            return "El modo RANKED requiere exactamente 5 jugadores.";
        }

        if (numJugadores < 3) {
            return "Se necesitan al menos 3 jugadores para jugar.";
        }

        return null;
    }
}
