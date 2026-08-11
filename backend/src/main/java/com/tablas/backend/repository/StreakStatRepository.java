package com.tablas.backend.repository;

import com.tablas.backend.model.QuizMode;
import com.tablas.backend.model.StreakStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StreakStatRepository extends JpaRepository<StreakStat, UUID> {

    Optional<StreakStat> findByPlayerIdAndTableNumberAndMode(UUID playerId, int tableNumber, QuizMode mode);
}
