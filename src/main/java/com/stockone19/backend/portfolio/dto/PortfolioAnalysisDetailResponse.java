package com.stockone19.backend.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 포트폴리오 분석 상세 조회 API 응답 DTO
 */
public record PortfolioAnalysisDetailResponse(
        String status,
        String portfolioName,
        String analysisDate,
        AnalysisPeriod analysisPeriod,
        List<PortfolioResult> results
) {
    
    /**
     * 분석 기간
     */
    public record AnalysisPeriod(
            String startDate,
            String endDate
    ) {}
    
    /**
     * 포트폴리오 결과 (내 포트폴리오, 하방위험 최소화, 소르티노 비율 최적화)
     */
    public record PortfolioResult(
            String type,
            String riskLevel,
            Map<String, Double> holdings,
            Metrics metrics,
            List<String> strengths,
            List<String> weaknesses
    ) {}
    
    /**
     * 성과 지표 (PMPT 기반)
     */
    public record Metrics(
            @JsonProperty("expectedReturn")
            Double expectedReturn,
            @JsonProperty("sortinoRatio")
            Double sortinoRatio,
            @JsonProperty("downsideStd")
            Double downsideStd
    ) {}
    
    /**
     * 성공 응답 생성 헬퍼 메서드
     */
    public static PortfolioAnalysisDetailResponse of(
            String status,
            String portfolioName,
            String analysisDate,
            AnalysisPeriod analysisPeriod,
            List<PortfolioResult> results
    ) {
        return new PortfolioAnalysisDetailResponse(
                status,
                portfolioName,
                analysisDate,
                analysisPeriod,
                results
        );
    }
}

