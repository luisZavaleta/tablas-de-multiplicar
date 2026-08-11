package com.tablas.backend.dto;

import com.tablas.backend.model.Player;

import java.util.UUID;

public record PlayerResponse(UUID playerId, String name) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getName());
    }
}
