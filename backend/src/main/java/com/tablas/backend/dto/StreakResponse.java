package com.tablas.backend.dto;

public record StreakResponse(
        int table,
        String mode,
        int currentStreak,
        int bestStreak
) {
}
