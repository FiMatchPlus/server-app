package com.stockone19.backend.portfolio.domain;

import java.time.LocalDateTime;

public record Portfolio(
        Long id,
        String name,
        String description,
        String ruleId,
        boolean isMain,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long userId
) {

    public static Portfolio of(
            Long id,
            String name,
            String description,
            String ruleId,
            boolean isMain,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long userId
    ) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId
        );
    }

    public static Portfolio create(
            String name,
            String description,
            String ruleId,
            boolean isMain,
            Long userId
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new Portfolio(
                null, name, description, ruleId, isMain, now, now, userId
        );
    }

    public Portfolio withId(Long id) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId
        );
    }

    public Portfolio withUpdatedAt(LocalDateTime updatedAt) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId
        );
    }
}