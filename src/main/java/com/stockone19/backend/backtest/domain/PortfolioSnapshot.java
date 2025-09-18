package com.stockone19.backend.backtest.domain;

import java.time.LocalDateTime;

public record PortfolioSnapshot(
        Long id,
        Long portfolioId,
        double baseValue,
        double currentValue,
        LocalDateTime createdAt,
        String metricId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Double executionTime
) {

    public static PortfolioSnapshot of(
            Long id,
            Long portfolioId,
            double baseValue,
            double currentValue,
            LocalDateTime createdAt,
            String metricId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Double executionTime
    ) {
        return new PortfolioSnapshot(
                id, portfolioId, baseValue, currentValue, createdAt, 
                metricId, startAt, endAt, executionTime
        );
    }

    public static PortfolioSnapshot create(
            Long portfolioId,
            double baseValue,
            double currentValue,
            String metricId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Double executionTime
    ) {
        return new PortfolioSnapshot(
                null, portfolioId, baseValue, currentValue, LocalDateTime.now(), 
                metricId, startAt, endAt, executionTime
        );
    }

    public double getDailyReturn() {
        if (baseValue == 0) return 0.0;
        return ((currentValue - baseValue) / baseValue) * 100;
    }

    public double getDailyChange() {
        return currentValue - baseValue;
    }
}
