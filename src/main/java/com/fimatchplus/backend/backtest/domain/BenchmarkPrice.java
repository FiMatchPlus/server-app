package com.fimatchplus.backend.backtest.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 벤치마크 가격 데이터
 */
public record BenchmarkPrice(
        Long id,
        String indexCode,
        LocalDateTime datetime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal changeAmount,
        BigDecimal changeRate,
        Long volume,
        BigDecimal tradingValue,
        BigDecimal marketCap,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    
    public static BenchmarkPrice of(
            Long id,
            String indexCode,
            LocalDateTime datetime,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal changeAmount,
            BigDecimal changeRate,
            Long volume,
            BigDecimal tradingValue,
            BigDecimal marketCap,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new BenchmarkPrice(
                id, indexCode, datetime, openPrice, highPrice, lowPrice,
                closePrice, changeAmount, changeRate, volume, tradingValue,
                marketCap, createdAt, updatedAt
        );
    }
}
