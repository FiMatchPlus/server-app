package com.stockone19.backend.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * LLM이 생성한 포트폴리오 인사이트 리포트 DTO
 * report_result JSONB 컬럼 파싱용
 */
public record PortfolioInsightReport(
        @JsonProperty("portfolio_insights")
        List<PortfolioInsight> portfolioInsights,
        
        @JsonProperty("comparative_analysis")
        ComparativeAnalysis comparativeAnalysis,
        
        @JsonProperty("personalized_recommendation")
        PersonalizedRecommendation personalizedRecommendation
) {
    
    /**
     * 포트폴리오별 인사이트
     */
    public record PortfolioInsight(
            String type,
            
            @JsonProperty("performance_insight")
            PerformanceInsight performanceInsight,
            
            @JsonProperty("key_strengths")
            List<String> keyStrengths,
            
            @JsonProperty("key_weaknesses")
            List<String> keyWeaknesses,
            
            @JsonProperty("risk_profile")
            RiskProfile riskProfile
    ) {}
    
    /**
     * 성과 인사이트
     */
    public record PerformanceInsight(
            @JsonProperty("return_interpretation")
            String returnInterpretation,
            
            @JsonProperty("risk_interpretation")
            String riskInterpretation,
            
            @JsonProperty("efficiency_interpretation")
            String efficiencyInterpretation
    ) {}
    
    /**
     * 위험 프로필
     */
    public record RiskProfile(
            @JsonProperty("risk_level")
            String riskLevel,  // "저위험", "중위험", "고위험"
            
            String interpretation,
            String suitability
    ) {}
    
    /**
     * 비교 분석
     */
    public record ComparativeAnalysis(
            @JsonProperty("three_way_comparison")
            ThreeWayComparison threeWayComparison,
            
            @JsonProperty("decision_framework")
            DecisionFramework decisionFramework,
            
            @JsonProperty("key_differentiator")
            String keyDifferentiator
    ) {}
    
    /**
     * 3가지 포트폴리오 비교
     */
    public record ThreeWayComparison(
            @JsonProperty("return_perspective")
            String returnPerspective,
            
            @JsonProperty("risk_perspective")
            String riskPerspective,
            
            @JsonProperty("efficiency_perspective")
            String efficiencyPerspective
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
     * 맞춤형 추천
     */
    public record PersonalizedRecommendation(
            @JsonProperty("risk_tolerance_assessment")
            RiskToleranceAssessment riskToleranceAssessment,
            
            @JsonProperty("investment_horizon_assessment")
            InvestmentHorizonAssessment investmentHorizonAssessment,
            
            @JsonProperty("final_guidance")
            String finalGuidance
    ) {}
    
    /**
     * 위험 성향 평가
     */
    public record RiskToleranceAssessment(
            @JsonProperty("high_risk_tolerance")
            String highRiskTolerance,
            
            @JsonProperty("medium_risk_tolerance")
            String mediumRiskTolerance,
            
            @JsonProperty("low_risk_tolerance")
            String lowRiskTolerance
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
}


