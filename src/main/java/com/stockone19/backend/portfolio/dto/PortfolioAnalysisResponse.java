package com.stockone19.backend.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 포트폴리오 분석 엔진에서 콜백으로 받을 응답 DTO
 * 백테스트 엔진의 포트폴리오 분석 결과를 담는 구조
 */
public record PortfolioAnalysisResponse(
    Boolean success,
    
    // 최소 분산 포트폴리오 정보
    @JsonProperty("min_variance")
    PortfolioStrategyResponse minVariance,
    
    // 최대 샤프 비율 포트폴리오 정보
    @JsonProperty("max_sharpe")
    PortfolioStrategyResponse maxSharpe,
    
    // 성과 지표
    MetricsResponse metrics,
    
    // 벤치마크 비교 정보
    @JsonProperty("benchmark_comparison")
    BenchmarkComparisonResponse benchmarkComparison,
    
    // 종목별 상세 정보
    @JsonProperty("stock_details")
    Map<String, StockDetailResponse> stockDetails,
    
    // 포트폴리오 베타 분석
    @JsonProperty("portfolio_beta_analysis")
    BetaAnalysisResponse portfolioBetaAnalysis,
    
    // 분석 환경 정보
    @JsonProperty("risk_free_rate_used")
    Double riskFreeRateUsed,
    
    @JsonProperty("analysis_period")
    AnalysisPeriodResponse analysisPeriod,
    
    String notes,
    
    @JsonProperty("execution_time")
    Double executionTime,
    
    @JsonProperty("analysis_id")
    Long analysisId,
    
    String timestamp,
    
    // 백테스트 관련 정보 (기존 콜백과의 호환성)
    @JsonProperty("job_id")
    String jobId,
    
    @JsonProperty("portfolio_id")
    Long portfolioId
) {
    
    /**
     * 포트폴리오 전략 정보 (Min-Variance, Max-Sharpe)
     */
    public record PortfolioStrategyResponse(
        Map<String, Double> weights,
        @JsonProperty("beta_analysis")
        BetaAnalysisResponse betaAnalysis
    ) {}
    
    /**
     * 성과 지표 정보
     */
    public record MetricsResponse(
        @JsonProperty("min_variance")
        StrategyMetricsResponse minVariance,
        @JsonProperty("max_sharpe")
        StrategyMetricsResponse maxSharpe
    ) {}
    
    /**
     * 개별 전략의 성과 지표
     */
    public record StrategyMetricsResponse(
        @JsonProperty("expected_return")
        Double expectedReturn,
        
        @JsonProperty("std_deviation")
        Double stdDeviation,
        
        Double alpha,
        
        @JsonProperty("jensen_alpha")
        Double jensenAlpha,
        
        @JsonProperty("tracking_error")
        Double trackingError,
        
        @JsonProperty("sharpe_ratio")
        Double sharpeRatio,
        
        @JsonProperty("treynor_ratio")
        Double treynorRatio,
        
        @JsonProperty("sortino_ratio")
        Double sortinoRatio,
        
        @JsonProperty("calmar_ratio")
        Double calmarRatio,
        
        @JsonProperty("information_ratio")
        Double informationRatio,
        
        @JsonProperty("max_drawdown")
        Double maxDrawdown,
        
        @JsonProperty("downside_deviation")
        Double downsideDeviation,
        
        @JsonProperty("upside_beta")
        Double upsideBeta,
        
        @JsonProperty("downside_beta")
        Double downsideBeta,
        
        @JsonProperty("var_value")
        Double varValue,
        
        @JsonProperty("cvar_value")
        Double cvarValue,
        
        @JsonProperty("correlation_with_benchmark")
        Double correlationWithBenchmark
    ) {}
    
    /**
     * 벤치마크 비교 정보
     */
    public record BenchmarkComparisonResponse(
        @JsonProperty("benchmark_code")
        String benchmarkCode,
        
        @JsonProperty("benchmark_return")
        Double benchmarkReturn,
        
        @JsonProperty("benchmark_volatility")
        Double benchmarkVolatility,
        
        @JsonProperty("excess_return")
        Double excessReturn,
        
        @JsonProperty("relative_volatility")
        Double relativeVolatility,
        
        @JsonProperty("security_selection")
        Double securitySelection,
        
        @JsonProperty("timing_effect")
        Double timingEffect
    ) {}
    
    /**
     * 종목별 상세 정보
     */
    public record StockDetailResponse(
        @JsonProperty("expected_return")
        Double expectedReturn,
        
        Double volatility,
        
        @JsonProperty("correlation_to_portfolio")
        Double correlationToPortfolio,
        
        @JsonProperty("beta_analysis")
        BetaAnalysisResponse betaAnalysis
    ) {}
    
    /**
     * 베타 분석 정보
     */
    public record BetaAnalysisResponse(
        Double beta,
        @JsonProperty("r_square")
        Double rSquare,
        Double alpha
    ) {}
    
    /**
     * 분석 기간 정보
     */
    public record AnalysisPeriodResponse(
        String start,
        String end
    ) {}
}
