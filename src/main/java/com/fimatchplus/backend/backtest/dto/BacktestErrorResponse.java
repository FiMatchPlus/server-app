package com.fimatchplus.backend.backtest.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백테스트 실행 실패 시 클라이언트에게 전달하는 에러 응답
 */
@Builder
public record BacktestErrorResponse(
        ErrorDetail error,
        Double executionTime,
        String requestId
) {
    
    /**
     * 주가 데이터 누락 에러 응답 생성
     */
    public static BacktestErrorResponse createMissingStockDataError(
            String message,
            List<MissingStockData> missingData,
            String requestedPeriod,
            int totalStocks,
            int missingStocksCount,
            Double executionTime,
            String requestId) {
        
        ErrorDetail errorDetail = ErrorDetail.builder()
                .errorType("MISSING_STOCK_PRICE_DATA")
                .message(message)
                .missingData(missingData)
                .requestedPeriod(requestedPeriod)
                .totalStocks(totalStocks)
                .missingStocksCount(missingStocksCount)
                .timestamp(LocalDateTime.now())
                .build();
        
        return BacktestErrorResponse.builder()
                .error(errorDetail)
                .executionTime(executionTime)
                .requestId(requestId)
                .build();
    }
    
    /**
     * 일반적인 백테스트 실행 에러 응답 생성
     */
    public static BacktestErrorResponse createGeneralError(
            String errorType,
            String message,
            Double executionTime,
            String requestId) {
        
        ErrorDetail errorDetail = ErrorDetail.builder()
                .errorType(errorType)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        
        return BacktestErrorResponse.builder()
                .error(errorDetail)
                .executionTime(executionTime)
                .requestId(requestId)
                .build();
    }
    
    /**
     * 에러 상세 정보
     */
    @Builder
    public record ErrorDetail(
            String errorType,
            String message,
            List<MissingStockData> missingData,
            String requestedPeriod,
            Integer totalStocks,
            Integer missingStocksCount,
            LocalDateTime timestamp
    ) {}
}
