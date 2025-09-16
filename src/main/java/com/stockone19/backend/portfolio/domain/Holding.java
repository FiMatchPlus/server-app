package com.stockone19.backend.portfolio.domain;

import java.time.LocalDateTime;

public record Holding(
        Long id,
        Long portfolioId,
        String symbol,
        Integer shares,
        double currentPrice,
        double totalValue,
        Double changeAmount,
        Double changePercent,
        double weight,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static Holding of(
            Long id,
            Long portfolioId,
            String symbol,
            Integer shares,
            double currentPrice,
            double totalValue,
            Double changeAmount,
            Double changePercent,
            double weight,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Holding(
                id, portfolioId, symbol, shares, currentPrice, totalValue,
                changeAmount, changePercent, weight, createdAt, updatedAt
        );
    }

    public static Holding create(
            Long portfolioId,
            String symbol,
            Integer shares,
            double currentPrice,
            double totalValue,
            Double changeAmount,
            Double changePercent,
            double weight
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new Holding(
                null, portfolioId, symbol, shares, currentPrice, totalValue,
                changeAmount, changePercent, weight, now, now
        );
    }
}


