package com.tablas.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AnswerRequest(
        @NotNull UUID playerId,
        @NotNull UUID questionId,
        @NotNull Integer answer
) {
}
