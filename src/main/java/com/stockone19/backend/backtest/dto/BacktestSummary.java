package com.stockone19.backend.backtest.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백테스트 상세 정보 응답 DTO
 */
public record BacktestSummary(
        Long id,
        String name,
        String period,
        Integer executionTime,
        LocalDateTime createdAt,
        BacktestMetrics metrics,
        List<DailyReturn> dailyReturns
) {
    
    public static BacktestSummary of(
            Long id,
            String name,
            String period,
            Integer executionTime,
            LocalDateTime createdAt,
            BacktestMetrics metrics,
            List<DailyReturn> dailyReturns
    ) {
        return new BacktestSummary(
                id, name, period, executionTime, createdAt, metrics, dailyReturns
        );
    }
}
