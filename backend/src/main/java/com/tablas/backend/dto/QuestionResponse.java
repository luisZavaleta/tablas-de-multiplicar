package com.tablas.backend.dto;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(UUID questionId, int factorA, int factorB, List<Integer> options) {
}
