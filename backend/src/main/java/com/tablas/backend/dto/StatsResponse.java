package com.tablas.backend.dto;

public record StatsResponse(
        String name,
        long totalScore,
        int level,
        int correctAnswers,
        int totalAnswers,
        double accuracy,
        int currentStreakMultipleChoice,
        int bestStreakMultipleChoice,
        int currentStreakTypeAnswer,
        int bestStreakTypeAnswer
) {
}
