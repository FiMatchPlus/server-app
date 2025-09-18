package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 백테스트 엔진에서 콜백으로 받을 응답 DTO
 */
public record BacktestCallbackResponse(
    @JsonProperty("job_id")
    String jobId,
    Boolean success,
    @JsonProperty("portfolio_snapshot")
    PortfolioSnapshotResponse portfolioSnapshot,
    BacktestExecutionResponse.BacktestMetricsResponse metrics,
    @JsonProperty("result_summary")
    List<BacktestExecutionResponse.DailyResultResponse> resultSummary,
    ErrorResponse error,
    @JsonProperty("execution_time")
    Double executionTime,
    @JsonProperty("request_id")
    String requestId,
    LocalDateTime timestamp
) {
    
    public record PortfolioSnapshotResponse(
        Long id,
        @JsonProperty("portfolio_id")
        Long portfolioId,
        @JsonProperty("base_value")
        Double baseValue,
        @JsonProperty("current_value")
        Double currentValue,
        @JsonProperty("start_at")
        LocalDateTime startAt,
        @JsonProperty("end_at")
        LocalDateTime endAt,
        @JsonProperty("created_at")
        LocalDateTime createdAt,
        @JsonProperty("execution_time")
        Double executionTime,
        List<HoldingResponse> holdings
    ) {}
    
    public record HoldingResponse(
        Long id,
        @JsonProperty("stock_id")
        String stockId,
        Integer quantity
    ) {}
    
    public record ErrorResponse(
        @JsonProperty("error_type")
        String errorType,
        String message,
        @JsonProperty("missing_data")
        List<MissingDataResponse> missingData,
        @JsonProperty("requested_period")
        String requestedPeriod,
        @JsonProperty("total_stocks")
        Integer totalStocks,
        @JsonProperty("missing_stocks_count")
        Integer missingStocksCount,
        LocalDateTime timestamp
    ) {}
    
    public record MissingDataResponse(
        @JsonProperty("stock_code")
        String stockCode,
        @JsonProperty("start_date")
        String startDate,
        @JsonProperty("end_date")
        String endDate,
        @JsonProperty("available_date_range")
        String availableDateRange
    ) {}
    
    // 편의 메서드들
    public String errorMessage() {
        return error != null ? error.message() : null;
    }
    
    public BacktestExecutionResponse toBacktestExecutionResponse() {
        if (!Boolean.TRUE.equals(success) || portfolioSnapshot == null) {
            return null;
        }
        
        // PortfolioSnapshotResponse 변환
        List<BacktestExecutionResponse.HoldingResponse> convertedHoldings = 
            portfolioSnapshot.holdings().stream()
                .map(h -> new BacktestExecutionResponse.HoldingResponse(
                    h.id(),
                    h.stockId(),
                    h.quantity()
                ))
                .toList();
                
        BacktestExecutionResponse.PortfolioSnapshotResponse convertedSnapshot = 
            new BacktestExecutionResponse.PortfolioSnapshotResponse(
                portfolioSnapshot.id(),
                portfolioSnapshot.portfolioId(),
                portfolioSnapshot.baseValue(),
                portfolioSnapshot.currentValue(),
                portfolioSnapshot.startAt(),
                portfolioSnapshot.endAt(),
                portfolioSnapshot.createdAt(),
                portfolioSnapshot.executionTime(),
                convertedHoldings
            );
        
        return new BacktestExecutionResponse(
            convertedSnapshot,
            metrics,
            resultSummary,
            executionTime != null ? executionTime : 0.0
        );
    }
}
