package com.stockone19.backend.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.dto.PortfolioAnalysisRequest;
import com.stockone19.backend.portfolio.dto.PortfolioAnalysisStartResponse;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 포트폴리오 분석 엔진 통신 서비스
 * 포트폴리오 분석 엔진과의 모든 통신을 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioAnalysisEngineClient {

    private final PortfolioRepository portfolioRepository;
    private final ObjectMapper objectMapper;

    @Qualifier("portfolioAnalysisEngineWebClient")
    private final WebClient portfolioAnalysisEngineWebClient;

    @Value("${portfolio.callback.base-url}")
    private String callbackBaseUrl;

    /**
     * 포트폴리오 분석 엔진에 비동기 요청 제출
     */
    @Async("backgroundTaskExecutor")
    public CompletableFuture<Void> submitToPortfolioAnalysisEngineAsync(Long portfolioId) {
        try {
            Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("포트폴리오를 찾을 수 없습니다: " + portfolioId));

            // 포트폴리오 분석 요청 생성
            PortfolioAnalysisRequest request = createPortfolioAnalysisRequest(portfolio);

            // 요청 body 로그 출력
            try {
                String requestBody = objectMapper.writeValueAsString(request);
                log.info("Sending portfolio analysis request to engine - portfolioId: {}, requestBody: {}", 
                        portfolioId, requestBody);
            } catch (Exception e) {
                log.warn("Failed to serialize request body for logging: {}", e.getMessage());
            }

            // 포트폴리오 분석 엔진에 요청
            PortfolioAnalysisStartResponse response = portfolioAnalysisEngineWebClient
                .post()
                .uri("/analysis/start")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PortfolioAnalysisStartResponse.class)
                .block();

            log.info("Portfolio analysis submitted to engine: portfolioId={}, analysisId={}",
                    portfolioId, response.analysisId());

        } catch (Exception e) {
            log.error("Failed to submit portfolio analysis to engine: portfolioId={}", portfolioId, e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 포트폴리오 분석 엔진 요청 생성
     */
    public PortfolioAnalysisRequest createPortfolioAnalysisRequest(Portfolio portfolio) {
        try {
            // 포트폴리오 보유 종목 조회
            List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolio.id());
            
            return PortfolioAnalysisRequest.of(
                portfolio.id(),
                holdings,
                callbackBaseUrl + "/portfolio-analysis/callback"
            );

        } catch (Exception e) {
            log.error("Failed to create portfolio analysis request for portfolioId: {}", portfolio.id(), e);
            throw new RuntimeException("Failed to create portfolio analysis request", e);
        }
    }
}
