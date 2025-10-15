package com.stockone19.backend.backtest.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * 일별 수익률 데이터 DTO
 */
public record DailyReturn(
        LocalDate date,
        Map<String, Double> stockReturns
) {
    
    public static DailyReturn of(LocalDate date, Map<String, Double> stockReturns) {
        return new DailyReturn(date, stockReturns);
    }
}
