package com.tablas.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Per-player record of how many times a specific multiplication fact
 * (table x multiplier) has been asked and answered correctly, used to bias
 * question selection toward facts the player hasn't mastered yet.
 */
@Entity
@Table(
        name = "fact_stat",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "table_number", "multiplier"})
)
public class FactStat {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private UUID playerId;
    private int tableNumber;
    private int multiplier;
    private int correctCount = 0;
    private int totalCount = 0;

    protected FactStat() {
        // JPA
    }

    public FactStat(UUID playerId, int tableNumber, int multiplier) {
        this.playerId = playerId;
        this.tableNumber = tableNumber;
        this.multiplier = multiplier;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void registerAttempt(boolean correct) {
        totalCount++;
        if (correct) {
            correctCount++;
        }
    }
}
