package com.impaintor.feature.game.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.impaintor.feature.room.models.Room;

@Entity
@Table(name = "game_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Room.Mode mode;

    private String secretWord;

    private Integer rounds;

    @Enumerated(EnumType.STRING)
    private Room.WinningSide winningSide;

    @Enumerated(EnumType.STRING)
    private Room.EndCondition endCondition;

    private LocalDateTime playedAt;

    @OneToMany(mappedBy = "gameRecord", cascade = CascadeType.ALL)
    @Builder.Default
    private List<GamePlayerRecord> playerRecords = new ArrayList<>();
}
