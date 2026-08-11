package com.tablas.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Per-player streak for one specific (table, mode) combination, so
 * practicing the table of 3 in Difícil mode has its own streak, separate
 * from the table of 7 in Normal mode.
 */
@Entity
@Table(
        name = "streak_stat",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "table_number", "mode"})
)
public class StreakStat {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private UUID playerId;
    private int tableNumber;

    @Enumerated(EnumType.STRING)
    private QuizMode mode;

    private int currentStreak = 0;
    private int bestStreak = 0;

    protected StreakStat() {
        // JPA
    }

    public StreakStat(UUID playerId, int tableNumber, QuizMode mode) {
        this.playerId = playerId;
        this.tableNumber = tableNumber;
        this.mode = mode;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public QuizMode getMode() {
        return mode;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public void registerCorrectAnswer() {
        currentStreak++;
        bestStreak = Math.max(bestStreak, currentStreak);
    }

    public void registerWrongAnswer() {
        currentStreak = 0;
    }
}
