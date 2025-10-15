package com.stockone19.backend.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 포트폴리오 분석 엔진에서 콜백으로 받을 응답 DTO
 * 백테스트 엔진의 포트폴리오 분석 결과를 담는 구조
 */
public record PortfolioAnalysisResponse(
    Boolean success,
    
    MetadataResponse metadata,
    
    BenchmarkInfoResponse benchmark,
    
    List<PortfolioStrategyResponse> portfolios,
    
    @JsonProperty("stock_details")
    Map<String, StockDetailResponse> stockDetails,
    
    @JsonProperty("job_id")
    String jobId
) {
    
    /**
     * 분석 메타데이터 정보
     */
    public record MetadataResponse(
        @JsonProperty("risk_free_rate_used")
        Double riskFreeRateUsed,
        
        PeriodResponse period,
        
        String notes,
        
        @JsonProperty("execution_time")
        Double executionTime,
        
        @JsonProperty("portfolio_id")
        Long portfolioId,
        
        String timestamp
    ) {}
    
    /**
     * 분석 기간 정보
     */
    public record PeriodResponse(
        String start,
        String end
    ) {}
    
    /**
     * 벤치마크 정보
     */
    public record BenchmarkInfoResponse(
        String code,
        @JsonProperty("return")
        Double benchmarkReturn,
        Double volatility
    ) {}
    
    /**
     * 포트폴리오 전략 정보 (user, min_downside_risk, max_sortino 공통 구조)
     */
    public record PortfolioStrategyResponse(
        String type,
        
        Map<String, Double> weights,
        
        @JsonProperty("beta_analysis")
        BetaAnalysisResponse betaAnalysis,
        
        MetricsResponse metrics,
        
        @JsonProperty("benchmark_comparison")
        BenchmarkComparisonResponse benchmarkComparison
    ) {}
    
    /**
     * 성과 지표 정보
     */
    public record MetricsResponse(
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
}
