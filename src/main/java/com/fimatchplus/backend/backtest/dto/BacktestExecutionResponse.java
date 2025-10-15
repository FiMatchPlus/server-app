package com.fimatchplus.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 외부 백테스트 서버로부터 받는 응답 DTO
 */
public record BacktestExecutionResponse(
        @JsonProperty("portfolio_snapshot")
        PortfolioSnapshotResponse portfolioSnapshot,
        BacktestMetricsResponse metrics,
        @JsonProperty("result_summary")
        List<DailyResultResponse> resultSummary,
        @JsonProperty("execution_time")
        double executionTime
) {
    
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
            @JsonProperty("execution_time")
            double executionTime,
            List<HoldingResponse> holdings
    ) {}
    
    public record HoldingResponse(
            Long id,
            @JsonProperty("stock_id")
            String stockId,
            int quantity
    ) {}
    
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
    
    public record DailyResultResponse(
            LocalDateTime date,
            @JsonProperty("stocks")
            List<DailyStockResponse> stocks
    ) {}
    
        public record DailyStockResponse(
            @JsonProperty("stock_code")
            String stockCode,
            LocalDateTime date,
            @JsonProperty("close_price")
            double closePrice,
            @JsonProperty("daily_return")
            double dailyReturn,
            @JsonProperty("portfolio_weight")
            double portfolioWeight,
            @JsonProperty("portfolio_contribution")
            double portfolioContribution,
            int quantity
    ) {
        /**
         * 보유 가치 계산 (quantity × closePrice)
         */
        public double getValue() {
            return quantity * closePrice;
        }
    }
}
