package com.stockone19.backend.backtest.dto;

/**
 * 백테스트 결과 지표
 */
public record BacktestMetrics(
        double totalReturn,      // 총 수익률
        double annualizedReturn, // 연환산 수익률
        double volatility,       // 변동성
        double sharpeRatio,      // 샤프 비율
        double maxDrawdown,      // 최대 낙폭
        double winRate,          // 승률
        double profitLossRatio   // 손익비
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
