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

    public void registerCorrectAnswer() {
        correctAnswers++;
        totalAnswers++;
    }

    public void registerWrongAnswer() {
        totalAnswers++;
    }

    public void addScore(long points) {
        totalScore += points;
    }
}
