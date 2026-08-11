package com.tablas.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String name;

    private long totalScore = 0;
    private int correctAnswers = 0;
    private int totalAnswers = 0;

    // Streaks are tracked per quiz mode, not shared, so switching to
    // Difícil mode doesn't reset (or inherit) progress made in Normal mode.
    private int currentStreakMultipleChoice = 0;
    private int bestStreakMultipleChoice = 0;
    private int currentStreakTypeAnswer = 0;
    private int bestStreakTypeAnswer = 0;

    protected Player() {
        // JPA
    }

    public Player(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getTotalScore() {
        return totalScore;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public int getTotalAnswers() {
        return totalAnswers;
    }

    public int getLevel() {
        return (int) (totalScore / 100) + 1;
    }

    public int getCurrentStreak(QuizMode mode) {
        return mode == QuizMode.MULTIPLE_CHOICE ? currentStreakMultipleChoice : currentStreakTypeAnswer;
    }

    public int getBestStreak(QuizMode mode) {
        return mode == QuizMode.MULTIPLE_CHOICE ? bestStreakMultipleChoice : bestStreakTypeAnswer;
    }

    public void registerCorrectAnswer(QuizMode mode) {
        correctAnswers++;
        totalAnswers++;
        if (mode == QuizMode.MULTIPLE_CHOICE) {
            currentStreakMultipleChoice++;
            bestStreakMultipleChoice = Math.max(bestStreakMultipleChoice, currentStreakMultipleChoice);
        } else {
            currentStreakTypeAnswer++;
            bestStreakTypeAnswer = Math.max(bestStreakTypeAnswer, currentStreakTypeAnswer);
        }
    }

    public void registerWrongAnswer(QuizMode mode) {
        totalAnswers++;
        if (mode == QuizMode.MULTIPLE_CHOICE) {
            currentStreakMultipleChoice = 0;
        } else {
            currentStreakTypeAnswer = 0;
        }
    }

    public void addScore(long points) {
        totalScore += points;
    }
}
