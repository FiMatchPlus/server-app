package com.stockone19.backend.backtest.domain;

import java.time.LocalDateTime;

public record PortfolioSnapshot(
        Long id,
        Long backtestId,
        double baseValue,
        double currentValue,
        LocalDateTime createdAt,
        String metrics,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Double executionTime
) {

    public static PortfolioSnapshot of(
            Long id,
            Long backtestId,
            double baseValue,
            double currentValue,
            LocalDateTime createdAt,
            String metrics,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Double executionTime
    ) {
        return new PortfolioSnapshot(
                id, backtestId, baseValue, currentValue, createdAt, 
                metrics, startAt, endAt, executionTime
        );
    }

    public static PortfolioSnapshot create(
            Long backtestId,
            double baseValue,
            double currentValue,
            String metrics,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Double executionTime
    ) {
        return new PortfolioSnapshot(
                null, backtestId, baseValue, currentValue, LocalDateTime.now(), 
                metrics, startAt, endAt, executionTime
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
