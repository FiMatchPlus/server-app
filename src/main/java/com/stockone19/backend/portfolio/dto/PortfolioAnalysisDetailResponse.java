package com.stockone19.backend.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 포트폴리오 분석 상세 조회 API 응답 DTO
 */
public record PortfolioAnalysisDetailResponse(
        String status,
        String portfolioName,
        String analysisDate,
        AnalysisPeriod analysisPeriod,
        @JsonProperty("execution_time")
        Double executionTime,
        List<PortfolioInsight> results,
        @JsonProperty("comparative_analysis")
        ComparativeAnalysis comparativeAnalysis,
        @JsonProperty("personalized_recommendation")
        PersonalizedRecommendation personalizedRecommendation
) {
    
    /**
     * 분석 기간
     */
    public record AnalysisPeriod(
            String startDate,
            String endDate
    ) {}
    
    /**
     * 포트폴리오 인사이트 (내 포트폴리오, 하방위험 최소화, 소르티노 비율 최적화)
     */
    public record PortfolioInsight(
            String type,
            String riskLevel,
            List<HoldingInfo> holdings,
            Metrics metrics,
            @JsonProperty("risk_profile")
            RiskProfile riskProfile,
            @JsonProperty("key_strengths")
            List<String> keyStrengths,
            @JsonProperty("key_weaknesses")
            List<String> keyWeaknesses,
            @JsonProperty("performance_insight")
            PerformanceInsight performanceInsight
    ) {}
    
    /**
     * 보유 종목 정보
     */
    public record HoldingInfo(
            String code,
            String name,
            Double weight
    ) {}
    
    /**
     * 성과 지표 (PMPT 기반)
     */
    public record Metrics(
            @JsonProperty("expectedReturn")
            Double expectedReturn,
            @JsonProperty("downsideStd")
            Double downsideStd,
            @JsonProperty("sortinoRatio")
            Double sortinoRatio
    ) {}
    
    /**
     * 위험 프로필
     */
    public record RiskProfile(
            @JsonProperty("risk_level")
            String riskLevel,
            String suitability,
            String interpretation
    ) {}
    
    /**
     * 성과 인사이트
     */
    public record PerformanceInsight(
            @JsonProperty("risk_interpretation")
            String riskInterpretation,
            @JsonProperty("return_interpretation")
            String returnInterpretation,
            @JsonProperty("efficiency_interpretation")
            String efficiencyInterpretation
    ) {}
    
    /**
     * 비교 분석
     */
    public record ComparativeAnalysis(
            @JsonProperty("key_differentiator")
            String keyDifferentiator,
            @JsonProperty("decision_framework")
            DecisionFramework decisionFramework,
            @JsonProperty("three_way_comparison")
            ThreeWayComparison threeWayComparison
    ) {}
    
    /**
     * 의사결정 프레임워크
     */
    public record DecisionFramework(
            @JsonProperty("choose_user_portfolio_if")
            List<String> chooseUserPortfolioIf,
            @JsonProperty("choose_min_downside_risk_if")
            List<String> chooseMinDownsideRiskIf,
            @JsonProperty("choose_max_sortino_if")
            List<String> chooseMaxSortinoIf
    ) {}
    
    /**
     * 3가지 포트폴리오 비교
     */
    public record ThreeWayComparison(
            @JsonProperty("risk_perspective")
            String riskPerspective,
            @JsonProperty("return_perspective")
            String returnPerspective,
            @JsonProperty("efficiency_perspective")
            String efficiencyPerspective
    ) {}
    
    /**
     * 맞춤형 추천
     */
    public record PersonalizedRecommendation(
            @JsonProperty("final_guidance")
            String finalGuidance,
            @JsonProperty("risk_tolerance_assessment")
            RiskToleranceAssessment riskToleranceAssessment,
            @JsonProperty("investment_horizon_assessment")
            InvestmentHorizonAssessment investmentHorizonAssessment
    ) {}
    
    /**
     * 위험 성향 평가
     */
    public record RiskToleranceAssessment(
            @JsonProperty("low_risk_tolerance")
            String lowRiskTolerance,
            @JsonProperty("medium_risk_tolerance")
            String mediumRiskTolerance,
            @JsonProperty("high_risk_tolerance")
            String highRiskTolerance
    ) {}
    
    /**
     * 투자 기간 평가
     */
    public record InvestmentHorizonAssessment(
            @JsonProperty("short_term")
            String shortTerm,
            @JsonProperty("medium_term")
            String mediumTerm,
            @JsonProperty("long_term")
            String longTerm
    ) {}
    
    /**
     * 성공 응답 생성 헬퍼 메서드
     */
    public static PortfolioAnalysisDetailResponse of(
            String status,
            String portfolioName,
            String analysisDate,
            AnalysisPeriod analysisPeriod,
            Double executionTime,
            List<PortfolioInsight> results,
            ComparativeAnalysis comparativeAnalysis,
            PersonalizedRecommendation personalizedRecommendation
    ) {
        return new PortfolioAnalysisDetailResponse(
                status,
                portfolioName,
                analysisDate,
                analysisPeriod,
                executionTime,
                results,
                comparativeAnalysis,
                personalizedRecommendation
        );
    }
}

