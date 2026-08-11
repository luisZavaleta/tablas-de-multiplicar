package com.tablas.backend.model;

/**
 * In-memory record of a question that was handed to the client, kept until
 * answered so the correct value never has to be trusted from the client.
 * Also remembers which mode it was asked in, so the matching streak is the
 * one updated on answer, regardless of what the client claims.
 */
public record PendingQuestion(int factorA, int factorB, int correctAnswer, QuizMode mode) {
}
