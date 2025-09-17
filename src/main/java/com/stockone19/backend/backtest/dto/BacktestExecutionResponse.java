package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 외부 백테스트 서버로부터 받는 응답 DTO
 */
@Builder
public record BacktestExecutionResponse(
        @JsonProperty("portfolio_snapshot")
        PortfolioSnapshotResponse portfolioSnapshot,
        BacktestMetricsResponse metrics,
        @JsonProperty("result_summary")
        List<DailyResultResponse> resultSummary,
        @JsonProperty("execution_time")
        double executionTime
) {
    
    @Builder
    public record PortfolioSnapshotResponse(
            Long id,
            @JsonProperty("portfolio_id")
            Long portfolioId,
            @JsonProperty("base_value")
            double baseValue,
            @JsonProperty("current_value")
            double currentValue,
            @JsonProperty("start_at")
            LocalDateTime startAt,
            @JsonProperty("end_at")
            LocalDateTime endAt,
            @JsonProperty("created_at")
            LocalDateTime createdAt,
            @JsonProperty("metric_id")
            String metricId,
            @JsonProperty("execution_time")
            double executionTime,
            List<HoldingResponse> holdings
    ) {}
    
    @Builder
    public record HoldingResponse(
            Long id,
            @JsonProperty("stock_id")
            String stockId,
            double weight,
            double price,
            int quantity,
            double value,
            @JsonProperty("recorded_at")
            LocalDateTime recordedAt
    ) {}
    
    @Builder
    public record BacktestMetricsResponse(
            @JsonProperty("total_return")
            double totalReturn,
            @JsonProperty("annualized_return")
            double annualizedReturn,
            double volatility,
            @JsonProperty("sharpe_ratio")
            double sharpeRatio,
            @JsonProperty("max_drawdown")
            double maxDrawdown,
            @JsonProperty("var_95")
            double var95,
            @JsonProperty("var_99")
            double var99,
            @JsonProperty("cvar_95")
            double cvar95,
            @JsonProperty("cvar_99")
            double cvar99,
            @JsonProperty("win_rate")
            double winRate,
            @JsonProperty("profit_loss_ratio")
            double profitLossRatio
    ) {}
    
    @Builder
    public record DailyResultResponse(
            LocalDateTime date,
            @JsonProperty("portfolio_return")
            double portfolioReturn,
            @JsonProperty("portfolio_value")
            double portfolioValue,
            @JsonProperty("sharpe_ratio")
            Double sharpeRatio
    ) {}
}
