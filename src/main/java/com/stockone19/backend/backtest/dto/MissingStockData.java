package com.stockone19.backend.backtest.dto;

import lombok.Builder;

/**
 * 누락된 주가 데이터 정보
 */
@Builder
public record MissingStockData(
        String stockCode,
        String startDate,
        String endDate,
        String availableDateRange
) {
    
    /**
     * 사용 가능한 데이터가 없는 경우
     */
    public static MissingStockData withNoAvailableData(String stockCode, String startDate, String endDate) {
        return MissingStockData.builder()
                .stockCode(stockCode)
                .startDate(startDate)
                .endDate(endDate)
                .availableDateRange(null)
                .build();
    }
    
    /**
     * 사용 가능한 데이터 기간이 있는 경우
     */
    public static MissingStockData withAvailableData(String stockCode, String startDate, String endDate, String availableDateRange) {
        return MissingStockData.builder()
                .stockCode(stockCode)
                .startDate(startDate)
                .endDate(endDate)
                .availableDateRange(availableDateRange)
                .build();
    }
}
