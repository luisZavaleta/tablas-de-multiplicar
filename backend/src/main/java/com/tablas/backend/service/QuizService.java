package com.tablas.backend.service;

import com.tablas.backend.dto.AnswerResponse;
import com.tablas.backend.dto.QuestionResponse;
import com.tablas.backend.dto.StatsResponse;
import com.tablas.backend.model.FactStat;
import com.tablas.backend.model.PendingQuestion;
import com.tablas.backend.model.Player;
import com.tablas.backend.model.QuizMode;
import com.tablas.backend.repository.FactStatRepository;
import com.tablas.backend.repository.PlayerRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private static final long POINTS_PER_CORRECT_ANSWER = 10;
    private static final long STREAK_BONUS = 5;
    private static final int STREAK_BONUS_EVERY = 5;
    private static final int MULTIPLIERS_PER_TABLE = 10;

    private final PlayerRepository playerRepository;
    private final FactStatRepository factStatRepository;
    private final Map<UUID, PendingQuestion> pendingQuestions = new ConcurrentHashMap<>();

    public QuizService(PlayerRepository playerRepository, FactStatRepository factStatRepository) {
        this.playerRepository = playerRepository;
        this.factStatRepository = factStatRepository;
    }

    public QuestionResponse generateQuestion(UUID playerId, List<Integer> activeTables, QuizMode mode) {
        if (!playerRepository.existsById(playerId)) {
            throw new IllegalArgumentException("Player not found");
        }

        List<Integer> tables = activeTables.isEmpty() ? List.of(7) : activeTables;
        List<int[]> facts = new ArrayList<>();
        for (int table : tables) {
            for (int multiplier = 1; multiplier <= MULTIPLIERS_PER_TABLE; multiplier++) {
                facts.add(new int[]{table, multiplier});
            }
        }

        Map<String, Integer> correctCountsByFact = factStatRepository.findByPlayerId(playerId).stream()
                .collect(Collectors.toMap(
                        stat -> factKey(stat.getTableNumber(), stat.getMultiplier()),
                        FactStat::getCorrectCount
                ));

        int[] chosenFact = pickWeightedFact(facts, correctCountsByFact);
        int factorA = chosenFact[0];
        int factorB = chosenFact[1];
        int correctAnswer = factorA * factorB;

        UUID questionId = UUID.randomUUID();
        pendingQuestions.put(questionId, new PendingQuestion(factorA, factorB, correctAnswer, mode));

        List<Integer> options = mode == QuizMode.MULTIPLE_CHOICE
                ? generateOptions(correctAnswer, factorA)
                : List.of();
        return new QuestionResponse(questionId, factorA, factorB, options);
    }

    /**
     * Picks a fact with probability inversely proportional to how many times
     * the player has already answered it correctly, so mastered facts keep
     * showing up (never zero chance) but much less often than unpracticed ones.
     */
    private int[] pickWeightedFact(List<int[]> facts, Map<String, Integer> correctCountsByFact) {
        double[] weights = new double[facts.size()];
        double totalWeight = 0;
        for (int i = 0; i < facts.size(); i++) {
            int[] fact = facts.get(i);
            int correctCount = correctCountsByFact.getOrDefault(factKey(fact[0], fact[1]), 0);
            weights[i] = 1.0 / (correctCount + 1);
            totalWeight += weights[i];
        }

        double roll = Math.random() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < facts.size(); i++) {
            cumulative += weights[i];
            if (roll <= cumulative) {
                return facts.get(i);
            }
        }
        return facts.get(facts.size() - 1);
    }

    private String factKey(int table, int multiplier) {
        return table + ":" + multiplier;
    }

    public AnswerResponse submitAnswer(UUID playerId, UUID questionId, int answer) {
        PendingQuestion question = pendingQuestions.remove(questionId);
        if (question == null) {
            throw new IllegalArgumentException("Question not found or already answered");
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        QuizMode mode = question.mode();
        boolean correct = answer == question.correctAnswer();
        long pointsEarned = 0;

        if (correct) {
            player.registerCorrectAnswer(mode);
            pointsEarned = POINTS_PER_CORRECT_ANSWER;
            if (player.getCurrentStreak(mode) % STREAK_BONUS_EVERY == 0) {
                pointsEarned += STREAK_BONUS;
            }
            player.addScore(pointsEarned);
        } else {
            player.registerWrongAnswer(mode);
        }

        playerRepository.save(player);
        recordFactAttempt(playerId, question.factorA(), question.factorB(), correct);

        return new AnswerResponse(
                correct,
                question.correctAnswer(),
                pointsEarned,
                player.getTotalScore(),
                player.getCurrentStreak(mode),
                player.getLevel()
        );
    }

    private void recordFactAttempt(UUID playerId, int table, int multiplier, boolean correct) {
        FactStat stat = factStatRepository
                .findByPlayerIdAndTableNumberAndMultiplier(playerId, table, multiplier)
                .orElseGet(() -> new FactStat(playerId, table, multiplier));
        stat.registerAttempt(correct);
        factStatRepository.save(stat);
    }

    public StatsResponse stats(Player player) {
        double accuracy = player.getTotalAnswers() == 0
                ? 0.0
                : (100.0 * player.getCorrectAnswers() / player.getTotalAnswers());

        return new StatsResponse(
                player.getName(),
                player.getTotalScore(),
                player.getLevel(),
                player.getCorrectAnswers(),
                player.getTotalAnswers(),
                accuracy,
                player.getCurrentStreak(QuizMode.MULTIPLE_CHOICE),
                player.getBestStreak(QuizMode.MULTIPLE_CHOICE),
                player.getCurrentStreak(QuizMode.TYPE_ANSWER),
                player.getBestStreak(QuizMode.TYPE_ANSWER)
        );
    }

    private List<Integer> generateOptions(int correctAnswer, int table) {
        Set<Integer> options = new LinkedHashSet<>();
        options.add(correctAnswer);

        int attempts = 0;
        while (options.size() < 4 && attempts < 50) {
            attempts++;
            int offset = (1 + (int) (Math.random() * 3)) * table;
            boolean subtract = Math.random() < 0.5;
            int candidate = subtract ? correctAnswer - offset : correctAnswer + offset;
            if (candidate > 0) {
                options.add(candidate);
            }
        }

        List<Integer> shuffled = new ArrayList<>(options);
        java.util.Collections.shuffle(shuffled);
        return shuffled;
    }
}
