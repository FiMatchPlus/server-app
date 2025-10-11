package com.stockone19.backend.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.dto.PortfolioAnalysisDetailResponse;
import com.stockone19.backend.portfolio.dto.PortfolioAnalysisResponse;
import com.stockone19.backend.portfolio.dto.PortfolioInsightReport;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 포트폴리오 분석 상세 조회 서비스
 * analysis_result와 report_result를 파싱하여 상세 정보를 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioAnalysisDetailService {

    private final PortfolioRepository portfolioRepository;
    private final ObjectMapper objectMapper;

    /**
     * 포트폴리오 분석 상세 조회 (리포트 포함)
     * analysis_result와 report_result를 파싱하여 상세 정보 반환
     * 
     * @param portfolioId 포트폴리오 ID
     * @return 포트폴리오 분석 상세 정보
     */
    public PortfolioAnalysisDetailResponse getPortfolioAnalysisDetail(Long portfolioId) {
        log.info("Getting portfolio analysis detail for portfolioId: {}", portfolioId);

        // 1. Portfolio 조회
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", "id", portfolioId));

        // 2. analysis_result 파싱
        if (portfolio.analysisResult() == null || portfolio.analysisResult().trim().isEmpty()) {
            throw new RuntimeException("포트폴리오 분석 결과가 없습니다. 분석이 완료되지 않았을 수 있습니다.");
        }

        PortfolioAnalysisResponse analysisResponse;
        try {
            analysisResponse = objectMapper.readValue(
                    portfolio.analysisResult(), 
                    PortfolioAnalysisResponse.class
            );
        } catch (Exception e) {
            log.error("Failed to parse analysis_result for portfolioId: {}", portfolioId, e);
            throw new RuntimeException("포트폴리오 분석 결과 파싱에 실패했습니다.", e);
        }

        // 3. report_result 파싱 (null 가능)
        PortfolioInsightReport insightReport = null;
        if (portfolio.reportResult() != null && !portfolio.reportResult().trim().isEmpty()) {
            try {
                insightReport = objectMapper.readValue(
                        portfolio.reportResult(), 
                        PortfolioInsightReport.class
                );
            } catch (Exception e) {
                log.warn("Failed to parse report_result for portfolioId: {}, will use null", portfolioId, e);
                // report_result 파싱 실패 시 null로 처리 (선택적 데이터)
            }
        }

        // 4. 응답 생성
        return buildDetailResponse(portfolio, analysisResponse, insightReport);
    }

    /**
     * 상세 응답 생성
     */
    private PortfolioAnalysisDetailResponse buildDetailResponse(
            Portfolio portfolio,
            PortfolioAnalysisResponse analysisResponse,
            PortfolioInsightReport insightReport
    ) {
        // 분석 기간 추출
        PortfolioAnalysisDetailResponse.AnalysisPeriod analysisPeriod = 
                new PortfolioAnalysisDetailResponse.AnalysisPeriod(
                        analysisResponse.metadata().period().start(),
                        analysisResponse.metadata().period().end()
                );

        // 인사이트를 type별로 매핑 (빠른 조회를 위해)
        Map<String, PortfolioInsightReport.PortfolioInsight> insightMap = new HashMap<>();
        if (insightReport != null && insightReport.portfolioInsights() != null) {
            insightMap = insightReport.portfolioInsights().stream()
                    .collect(Collectors.toMap(
                            PortfolioInsightReport.PortfolioInsight::type,
                            insight -> insight,
                            (existing, replacement) -> replacement
                    ));
        }

        // 포트폴리오 결과 리스트 생성
        List<PortfolioAnalysisDetailResponse.PortfolioResult> results = new ArrayList<>();
        
        if (analysisResponse.portfolios() != null) {
            for (PortfolioAnalysisResponse.PortfolioStrategyResponse portfolioStrategy : analysisResponse.portfolios()) {
                String type = portfolioStrategy.type();
                
                // 인사이트 조회 (없을 수 있음)
                PortfolioInsightReport.PortfolioInsight insight = insightMap.get(type);
                
                // risk_level 변환 (한국어 → 영어)
                String riskLevel = null;
                List<String> strengths = null;
                List<String> weaknesses = null;
                
                if (insight != null) {
                    riskLevel = convertRiskLevel(insight.riskProfile().riskLevel());
                    strengths = insight.keyStrengths();
                    weaknesses = insight.keyWeaknesses();
                }
                
                // metrics 추출
                PortfolioAnalysisDetailResponse.Metrics metrics = 
                        new PortfolioAnalysisDetailResponse.Metrics(
                                portfolioStrategy.metrics().stdDeviation(),
                                portfolioStrategy.metrics().sharpeRatio(),
                                portfolioStrategy.metrics().expectedReturn()
                        );
                
                // 포트폴리오 결과 생성
                PortfolioAnalysisDetailResponse.PortfolioResult result = 
                        new PortfolioAnalysisDetailResponse.PortfolioResult(
                                type,
                                riskLevel,
                                portfolioStrategy.weights(),
                                metrics,
                                strengths,
                                weaknesses
                        );
                
                results.add(result);
            }
        }

        // 최종 응답 생성
        return PortfolioAnalysisDetailResponse.of(
                portfolio.status().name(),
                portfolio.name(),
                analysisResponse.metadata().timestamp(),
                analysisPeriod,
                results
        );
    }

    /**
     * 위험 수준 변환 (한국어 → 영어)
     */
    private String convertRiskLevel(String koreanRiskLevel) {
        if (koreanRiskLevel == null) {
            return null;
        }
        
        return switch (koreanRiskLevel.trim()) {
            case "저위험" -> "LOW";
            case "중위험" -> "MEDIUM";
            case "고위험" -> "HIGH";
            default -> {
                log.warn("Unknown risk level: {}", koreanRiskLevel);
                yield null;
            }
        };
    }
}

