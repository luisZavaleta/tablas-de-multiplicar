package com.tablas.backend.dto;

public record AnswerResponse(
        boolean correct,
        int correctAnswer,
        long pointsEarned,
        long totalScore,
        int streak,
        int level
) {
}
