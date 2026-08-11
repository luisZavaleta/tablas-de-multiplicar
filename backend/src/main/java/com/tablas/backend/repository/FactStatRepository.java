package com.tablas.backend.repository;

import com.tablas.backend.model.FactStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactStatRepository extends JpaRepository<FactStat, UUID> {

    List<FactStat> findByPlayerId(UUID playerId);

    Optional<FactStat> findByPlayerIdAndTableNumberAndMultiplier(UUID playerId, int tableNumber, int multiplier);
}
