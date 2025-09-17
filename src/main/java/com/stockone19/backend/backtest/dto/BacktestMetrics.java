package com.stockone19.backend.backtest.dto;

/**
 * 백테스트 결과 지표
 */
public record BacktestMetrics(
        double totalReturn,      // 총 수익률 (%)
        double annualizedReturn, // 연환산 수익률 (%)
        double volatility,       // 변동성 (%)
        double sharpeRatio,      // 샤프 비율
        double maxDrawdown,      // 최대 낙폭 (%)
        int winRate,            // 승률 (%)
        double avgWin,          // 평균 수익 (%)
        double avgLoss,         // 평균 손실 (%)
        int totalTrades         // 총 거래 횟수
) {
    
    public static BacktestMetrics of(
            double totalReturn,
            double annualizedReturn,
            double volatility,
            double sharpeRatio,
            double maxDrawdown,
            int winRate,
            double avgWin,
            double avgLoss,
            int totalTrades
    ) {
        return new BacktestMetrics(
                totalReturn, annualizedReturn, volatility, sharpeRatio,
                maxDrawdown, winRate, avgWin, avgLoss, totalTrades
        );
    }
}
