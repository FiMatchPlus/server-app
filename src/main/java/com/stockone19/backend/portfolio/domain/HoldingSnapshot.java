package com.stockone19.backend.portfolio.domain;

import java.time.LocalDateTime;

public record HoldingSnapshot(
        Long id,
        LocalDateTime recordedAt,
        double price,
        int quantity,
        double value,
        double weight,
        Long portfolioSnapshotId,
        String stockCode,
        double contribution,
        double dailyRatio
) {

    public static HoldingSnapshot of(
            Long id,
            LocalDateTime recordedAt,
            double price,
            int quantity,
            double value,
            double weight,
            Long portfolioSnapshotId,
            String stockCode,
            double contribution,
            double dailyRatio
    ) {
        return new HoldingSnapshot(
                id, recordedAt, price, quantity, value, weight, portfolioSnapshotId, stockCode, contribution, dailyRatio
        );
    }

    public static HoldingSnapshot create(
            double price,
            int quantity,
            double value,
            double weight,
            Long portfolioSnapshotId,
            String stockCode,
            double contribution,
            double dailyRatio
    ) {
        return new HoldingSnapshot(
                null, LocalDateTime.now(), price, quantity, value, weight, portfolioSnapshotId, stockCode, contribution, dailyRatio
        );
    }

    public static HoldingSnapshot createWithDate(
            double price,
            int quantity,
            double value,
            double weight,
            Long portfolioSnapshotId,
            String stockCode,
            LocalDateTime recordedAt,
            double contribution,
            double dailyRatio
    ) {
        return new HoldingSnapshot(
                null, recordedAt, price, quantity, value, weight, portfolioSnapshotId, stockCode, contribution, dailyRatio
        );
    }
}
