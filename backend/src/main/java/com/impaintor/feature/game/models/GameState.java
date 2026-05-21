package com.impaintor.feature.game.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Data;

@Data
public class GameState {
    private enum Phase {
        DRAWING, GALLERY, VOTING, TIE_BREAK, RESULT
    }

    private Phase phase = Phase.DRAWING;
    private final List<Long> drawingOrder = Collections.synchronizedList(new ArrayList<>());
    private final Set<Long> alivePlayers = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> canvasSnapshots = new ConcurrentHashMap<>();
    private String hintWord;
    private String roomCode;
    private int currentRound;
    private Long impostorId;
    private String secretWord;
    private List<Long> alivePlayerId;
    private Map<Long, Long> currentVotes;
    private int currentDrawerIndex = 0;
}
