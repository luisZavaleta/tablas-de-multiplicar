package com.tablas.backend.controller;

import com.tablas.backend.dto.AnswerRequest;
import com.tablas.backend.dto.AnswerResponse;
import com.tablas.backend.dto.CreatePlayerRequest;
import com.tablas.backend.dto.PlayerResponse;
import com.tablas.backend.dto.QuestionResponse;
import com.tablas.backend.dto.StatsResponse;
import com.tablas.backend.dto.StreakResponse;
import com.tablas.backend.model.Player;
import com.tablas.backend.model.QuizMode;
import com.tablas.backend.repository.PlayerRepository;
import com.tablas.backend.service.QuizService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizService quizService;
    private final PlayerRepository playerRepository;

    public QuizController(QuizService quizService, PlayerRepository playerRepository) {
        this.quizService = quizService;
        this.playerRepository = playerRepository;
    }

    @PostMapping("/player")
    public PlayerResponse createPlayer(@Valid @RequestBody CreatePlayerRequest request) {
        Player player = playerRepository.save(new Player(request.name()));
        return PlayerResponse.from(player);
    }

    @GetMapping("/quiz/question")
    public QuestionResponse question(
            @RequestParam UUID playerId,
            @RequestParam(defaultValue = "7") String tables,
            @RequestParam(defaultValue = "MULTIPLE_CHOICE") QuizMode mode
    ) {
        List<Integer> activeTables = Arrays.stream(tables.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
        return quizService.generateQuestion(playerId, activeTables, mode);
    }

    @PostMapping("/quiz/answer")
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        return quizService.submitAnswer(request.playerId(), request.questionId(), request.answer());
    }

    @GetMapping("/quiz/stats/{playerId}")
    public StatsResponse stats(@PathVariable UUID playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jugador no encontrado"));
        return quizService.stats(player);
    }

    @GetMapping("/quiz/streak")
    public StreakResponse streak(
            @RequestParam UUID playerId,
            @RequestParam int table,
            @RequestParam(defaultValue = "MULTIPLE_CHOICE") QuizMode mode
    ) {
        return quizService.getStreak(playerId, table, mode);
    }
}
