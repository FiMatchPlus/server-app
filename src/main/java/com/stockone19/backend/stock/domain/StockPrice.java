package com.stockone19.backend.stock.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockPrice(
        Long id,
        String stockId,
        LocalDateTime datetime,
        String intervalUnit,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        Long volume,
        BigDecimal changeAmount,
        BigDecimal changeRate
) {

    public static StockPrice of(
            Long id,
            String stockId,
            LocalDateTime datetime,
            String intervalUnit,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            Long volume,
            BigDecimal changeAmount,
            BigDecimal changeRate
    ) {
        return new StockPrice(
                id, stockId, datetime, intervalUnit, openPrice, highPrice,
                lowPrice, closePrice, volume, changeAmount, changeRate
        );
    }
}


