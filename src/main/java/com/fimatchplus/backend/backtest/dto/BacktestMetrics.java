package com.fimatchplus.backend.backtest.dto;

/**
 * 백테스트 결과 지표
 */
public record BacktestMetrics(
        double totalReturn,
        double annualizedReturn,
        double volatility,
        double sharpeRatio,
        double maxDrawdown,
        double winRate,
        double profitLossRatio
) {
    
    public static BacktestMetrics of(
            double totalReturn,
            double annualizedReturn,
            double volatility,
            double sharpeRatio,
            double maxDrawdown,
            double winRate,
            double profitLossRatio
    ) {
        return new BacktestMetrics(
                totalReturn, annualizedReturn, volatility, sharpeRatio,
                maxDrawdown, winRate, profitLossRatio
        );
    }
}
