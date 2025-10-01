package com.stockone19.backend.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockone19.backend.ai.service.PromptTemplateService;
import com.stockone19.backend.ai.service.ReportAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 포트폴리오 최적화 인사이트 리포트 생성 서비스
 * AI를 활용하여 MPT 기반 포트폴리오 최적화 결과를 분석하고 인사이트를 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioReportService {

    private final ReportAIService reportAIService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    /**
     * 포트폴리오 최적화 인사이트 리포트 생성
     *
     * @param portfolioId 포트폴리오 ID
     * @return 인사이트 리포트 텍스트
     */
    public String generateOptimizationInsight(Long portfolioId) {
        log.info("Generating portfolio optimization insight for portfolioId: {}", portfolioId);

        try {
            // 포트폴리오 데이터 생성 (사용자가 실제 데이터로 대체할 예정)
            String portfolioData = buildPortfolioData(portfolioId);

            // 프롬프트 템플릿 서비스를 사용하여 최적화 프롬프트 생성
            String optimizationPrompt = promptTemplateService.buildPortfolioOptimizationPrompt(portfolioData);

            // AI 서비스를 사용하여 인사이트 리포트 생성

            return reportAIService.generateResponse(
                    """
                            당신은 포트폴리오 최적화 전문가이자 친절한 투자 상담사입니다.
                            금융 지식이 많지 않은 일반 투자자가 **현대 포트폴리오 이론(MPT)** 기반의 최적화 결과를 이해하고,
                            자신의 포트폴리오를 개선할 수 있도록 **구체적이고 실행 가능한 인사이트**를 제공해야 합니다.
                            """,
                    optimizationPrompt
            );

        } catch (Exception e) {
            log.error("Failed to generate portfolio optimization insight for portfolioId: {}", portfolioId, e);
            throw new RuntimeException("포트폴리오 최적화 인사이트 생성에 실패했습니다.", e);
        }
    }

    /**
     * 포트폴리오 데이터를 JSON 형태로 구성
     * TODO: 실제 MPT 최적화 API 결과와 사용자 포트폴리오 데이터를 연동
     *
     * @param portfolioId 포트폴리오 ID
     * @return JSON 형태의 포트폴리오 데이터
     */
    private String buildPortfolioData(Long portfolioId) {
        try {
            // 샘플 데이터 구조 (사용자가 실제 데이터로 대체할 예정)
            Map<String, Object> data = new HashMap<>();

            // API_RESPONSE_JSON: MPT 최적화 결과
            Map<String, Object> apiResponse = new HashMap<>();
            apiResponse.put("success", true);

            // 1. 최소 분산 포트폴리오 비중
            Map<String, Object> minVariance = new HashMap<>();
            Map<String, Double> minVarianceWeights = new HashMap<>();
            minVarianceWeights.put("종목코드1", 0.3);
            minVarianceWeights.put("종목코드2", 0.4);
            minVarianceWeights.put("종목코드3", 0.3);
            minVariance.put("weights", minVarianceWeights);
            apiResponse.put("min_variance", minVariance);

            // 2. 최대 샤프 비율 포트폴리오 비중
            Map<String, Object> maxSharpe = new HashMap<>();
            Map<String, Double> maxSharpeWeights = new HashMap<>();
            maxSharpeWeights.put("종목코드1", 0.25);
            maxSharpeWeights.put("종목코드2", 0.45);
            maxSharpeWeights.put("종목코드3", 0.30);
            maxSharpe.put("weights", maxSharpeWeights);
            apiResponse.put("max_sharpe", maxSharpe);

            // 3. 백테스팅 기반 평균 성과 지표
            Map<String, Object> metrics = new HashMap<>();

            Map<String, Object> minVarianceMetrics = new HashMap<>();
            minVarianceMetrics.put("expected_return", 0.12);
            minVarianceMetrics.put("std_deviation", 0.18);
            minVarianceMetrics.put("beta", 0.95);
            minVarianceMetrics.put("alpha", 0.02);
            minVarianceMetrics.put("jensen_alpha", 0.015);
            minVarianceMetrics.put("tracking_error", 0.05);
            minVarianceMetrics.put("sharpe_ratio", 0.56);
            minVarianceMetrics.put("treynor_ratio", 0.11);
            minVarianceMetrics.put("sortino_ratio", 0.78);
            minVarianceMetrics.put("calmar_ratio", 1.2);
            minVarianceMetrics.put("information_ratio", 0.4);
            minVarianceMetrics.put("max_drawdown", -0.15);
            minVarianceMetrics.put("downside_deviation", 0.12);
            minVarianceMetrics.put("upside_beta", 0.92);
            minVarianceMetrics.put("downside_beta", 0.98);
            minVarianceMetrics.put("correlation_with_benchmark", 0.85);
            metrics.put("min_variance", minVarianceMetrics);

            Map<String, Object> maxSharpeMetrics = new HashMap<>();
            maxSharpeMetrics.put("expected_return", 0.15);
            maxSharpeMetrics.put("std_deviation", 0.20);
            maxSharpeMetrics.put("beta", 1.05);
            maxSharpeMetrics.put("alpha", 0.025);
            maxSharpeMetrics.put("jensen_alpha", 0.02);
            maxSharpeMetrics.put("tracking_error", 0.06);
            maxSharpeMetrics.put("sharpe_ratio", 0.65);
            maxSharpeMetrics.put("treynor_ratio", 0.13);
            maxSharpeMetrics.put("sortino_ratio", 0.88);
            maxSharpeMetrics.put("calmar_ratio", 1.5);
            maxSharpeMetrics.put("information_ratio", 0.5);
            maxSharpeMetrics.put("max_drawdown", -0.18);
            maxSharpeMetrics.put("downside_deviation", 0.14);
            maxSharpeMetrics.put("upside_beta", 1.02);
            maxSharpeMetrics.put("downside_beta", 1.08);
            maxSharpeMetrics.put("correlation_with_benchmark", 0.88);
            metrics.put("max_sharpe", maxSharpeMetrics);

            apiResponse.put("metrics", metrics);

            // 4. 벤치마크 및 분석 환경 정보
            Map<String, Object> benchmarkComparison = new HashMap<>();
            benchmarkComparison.put("benchmark_code", "KOSPI");
            benchmarkComparison.put("benchmark_return", 0.10);
            benchmarkComparison.put("benchmark_volatility", 0.16);
            benchmarkComparison.put("excess_return", 0.05);
            benchmarkComparison.put("relative_volatility", 1.25);
            benchmarkComparison.put("security_selection", 0.035);
            benchmarkComparison.put("timing_effect", 0.015);
            apiResponse.put("benchmark_comparison", benchmarkComparison);

            apiResponse.put("risk_free_rate_used", 0.03);

            Map<String, String> analysisPeriod = new HashMap<>();
            analysisPeriod.put("start", "2022-09-30T00:00:00Z");
            analysisPeriod.put("end", "2025-09-30T00:00:00Z");
            apiResponse.put("analysis_period", analysisPeriod);

            apiResponse.put("notes", "Analysis based on KOSPI benchmark and 3-year lookback period.");

            // 5. 종목별 상세 데이터
            Map<String, Object> stockDetails = new HashMap<>();

            Map<String, Object> stock1 = new HashMap<>();
            stock1.put("name", "종목명1");
            stock1.put("expected_return", 0.10);
            stock1.put("volatility", 0.22);
            stock1.put("correlation_to_portfolio", 0.6);
            stockDetails.put("종목코드1", stock1);

            Map<String, Object> stock2 = new HashMap<>();
            stock2.put("name", "종목명2");
            stock2.put("expected_return", 0.18);
            stock2.put("volatility", 0.30);
            stock2.put("correlation_to_portfolio", 0.4);
            stockDetails.put("종목코드2", stock2);

            Map<String, Object> stock3 = new HashMap<>();
            stock3.put("name", "종목명3");
            stock3.put("expected_return", 0.08);
            stock3.put("volatility", 0.15);
            stock3.put("correlation_to_portfolio", 0.7);
            stockDetails.put("종목코드3", stock3);

            apiResponse.put("stock_details", stockDetails);

            data.put("API_RESPONSE_JSON", apiResponse);

            // USER_PORTFOLIO_SNAPSHOT: 사용자 포트폴리오 현황
            Map<String, Object> userPortfolio = new HashMap<>();

            Map<String, Double> userWeights = new HashMap<>();
            userWeights.put("종목코드1", 0.60);
            userWeights.put("종목코드2", 0.20);
            userWeights.put("종목코드3", 0.20);
            userPortfolio.put("user_weights", userWeights);

            Map<String, Object> userMetrics = new HashMap<>();
            userMetrics.put("expected_return", 0.11);
            userMetrics.put("std_deviation", 0.22);
            userMetrics.put("sharpe_ratio", 0.45);
            userMetrics.put("max_drawdown", -0.25);
            userMetrics.put("beta", 1.10);
            userPortfolio.put("user_metrics_latest", userMetrics);

            Map<String, Double> riskContribution = new HashMap<>();
            riskContribution.put("종목코드1", 0.55);
            riskContribution.put("종목코드2", 0.30);
            riskContribution.put("종목코드3", 0.15);
            userPortfolio.put("stock_risk_contribution", riskContribution);

            data.put("USER_PORTFOLIO_SNAPSHOT", userPortfolio);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize portfolio data", e);
            throw new RuntimeException("포트폴리오 데이터 생성에 실패했습니다.", e);
        }
    }
}

