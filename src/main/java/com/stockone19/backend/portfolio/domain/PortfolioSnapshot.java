package com.stockone19.backend.portfolio.domain;

import java.time.LocalDateTime;

public record PortfolioSnapshot(
        Long id,
        LocalDateTime recordedAt,
        double baseValue,
        double currentValue,
        Long portfolioId
) {

    public static PortfolioSnapshot of(
            Long id,
            LocalDateTime recordedAt,
            double baseValue,
            double currentValue,
            Long portfolioId
    ) {
        return new PortfolioSnapshot(
                id, recordedAt, baseValue, currentValue, portfolioId
        );
    }

    public static PortfolioSnapshot create(
            double baseValue,
            double currentValue,
            Long portfolioId
    ) {
        return new PortfolioSnapshot(
                null, LocalDateTime.now(), baseValue, currentValue, portfolioId
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

