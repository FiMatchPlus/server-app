package com.fimatchplus.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
    @JsonProperty("backtest_id")
    Long backtestId,
    @JsonProperty("execution_logs")
    List<ExecutionLogResponse> executionLogs, 
    @JsonProperty("result_status")
    String resultStatus,
    @JsonProperty("benchmark_info")
    BenchmarkInfoResponse benchmarkInfo,
    @JsonProperty("benchmark_metrics")
    BenchmarkMetricsResponse benchmarkMetrics,
    @JsonProperty("risk_free_rate_info")
    RiskFreeRateInfoResponse riskFreeRateInfo,
    String timestamp
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
        String startAt,
        @JsonProperty("end_at")
        String endAt,
        @JsonProperty("created_at")
        String createdAt,
        @JsonProperty("execution_time")
        String executionTime,
        List<HoldingResponse> holdings
    ) {
        /**
         * execution_time을 Double로 변환
         * "1.666s" → 1.666
         */
        public Double getExecutionTimeAsDouble() {
            if (executionTime == null || executionTime.trim().isEmpty()) {
                return null;
            }
            try {
                String numericValue = executionTime.replaceAll("[^0-9.]", "");
                return Double.parseDouble(numericValue);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
    
    public record HoldingResponse(
        Long id,
        @JsonProperty("stock_id")
        String stockId,
        Integer quantity
    ) {}
    
    public record ExecutionLogResponse(
        LocalDateTime date,
        String action,
        String category,
        @JsonProperty("value")
        Double triggerValue,
        @JsonProperty("threshold")
        Double thresholdValue,
        String reason,
        @JsonProperty("portfolio_value")
        Double portfolioValue
    ) {}
    
    public record BenchmarkInfoResponse(
        @JsonProperty("benchmark_code")
        String benchmarkCode,
        @JsonProperty("latest_price")
        Double latestPrice,
        @JsonProperty("latest_date")
        LocalDateTime latestDate,
        @JsonProperty("data_range")
        BenchmarkDataRange dataRange,
        @JsonProperty("latest_change_rate")
        Double latestChangeRate
    ) {
        public record BenchmarkDataRange(
            @JsonProperty("start_date")
            LocalDateTime startDate,
            @JsonProperty("end_date")
            LocalDateTime endDate
        ) {}
    }
    
    public record BenchmarkMetricsResponse(
        @JsonProperty("benchmark_total_return")
        Double benchmarkTotalReturn,
        @JsonProperty("benchmark_volatility")
        Double benchmarkVolatility,
        @JsonProperty("benchmark_max_price")
        Double benchmarkMaxPrice,
        @JsonProperty("benchmark_min_price")
        Double benchmarkMinPrice,
        Double alpha,
        @JsonProperty("benchmark_daily_average")
        Double benchmarkDailyAverage
    ) {}
    
    public record RiskFreeRateInfoResponse(
        @JsonProperty("rate_type")
        String rateType,
        @JsonProperty("avg_annual_rate")
        Double avgAnnualRate,
        @JsonProperty("data_points")
        Integer dataPoints,
        @JsonProperty("decision_info")
        DecisionInfo decisionInfo,
        @JsonProperty("rate_info")
        RateInfo rateInfo
    ) {
        public record DecisionInfo(
            @JsonProperty("backtest_days")
            Integer backtestDays,
            @JsonProperty("period_classification")
            String periodClassification,
            @JsonProperty("selection_reason")
            String selectionReason
        ) {}
        
        public record RateInfo(
            @JsonProperty("rate_type")
            String rateType,
            @JsonProperty("latest_rate")
            Double latestRate,
            @JsonProperty("source")
            String source,
            @JsonProperty("data_range")
            RateDataRange dataRange
        ) {}
        
        public record RateDataRange(
            @JsonProperty("start_date")
            LocalDateTime startDate,
            @JsonProperty("end_date")
            LocalDateTime endDate
        ) {}
    }
    
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
        String timestamp
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
    
    public String errorMessage() {
        return error != null ? error.message() : null;
    }
    
    public BacktestExecutionResponse toBacktestExecutionResponse() {
        if (!Boolean.TRUE.equals(success) || portfolioSnapshot == null) {
            return null;
        }
        
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
                parseToLocalDateTime(portfolioSnapshot.startAt()),
                parseToLocalDateTime(portfolioSnapshot.endAt()),
                parseToLocalDateTime(portfolioSnapshot.createdAt()),
                portfolioSnapshot.getExecutionTimeAsDouble(),
                convertedHoldings
            );
        
        return new BacktestExecutionResponse(
            convertedSnapshot,
            metrics,
            resultSummary,
            executionTime != null ? executionTime : 0.0
        );
    }
    
    private static LocalDateTime parseToLocalDateTime(String isoString) {
        if (isoString == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(isoString).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }
}