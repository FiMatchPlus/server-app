package com.stockone19.backend.backtest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.dto.BacktestExecutionRequest;
import com.stockone19.backend.backtest.dto.BacktestStartResponse;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.common.service.BacktestJobMappingService;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 백테스트 엔진 통신 서비스
 * 백테스트 엔진과의 모든 통신을 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestEngineClient {

    private final BacktestRepository backtestRepository;
    private final BacktestJobMappingService jobMappingService;
    private final PortfolioRepository portfolioRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Qualifier("backtestEngineWebClient")
    private final WebClient backtestEngineWebClient;

    @Value("${backtest.callback.base-url}")
    private String callbackBaseUrl;

    /**
     * 백테스트 엔진에 비동기 요청 제출
     */
    @Async("backgroundTaskExecutor")
    public CompletableFuture<Void> submitToBacktestEngineAsync(Long backtestId) {
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("백테스트를 찾을 수 없습니다: " + backtestId));

            // 백테스트 요청 생성
            BacktestExecutionRequest request = createBacktestEngineRequest(backtest);

            // 요청 body 로그 출력
            try {
                String requestBody = objectMapper.writeValueAsString(request);
                log.info("Sending backtest request to engine - backtestId: {}, requestBody: {}", 
                        backtestId, requestBody);
            } catch (Exception e) {
                log.warn("Failed to serialize request body for logging: {}", e.getMessage());
            }

            // 백테스트 엔진에 요청
            BacktestStartResponse response = backtestEngineWebClient
                .post()
                .uri("/backtest/start")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(BacktestStartResponse.class)
                .block();

            // Redis에 매핑 저장
            jobMappingService.saveMapping(response.jobId(), backtestId);

            log.info("Backtest submitted to engine: backtestId={}, jobId={}", 
                    backtestId, response.jobId());

        } catch (Exception e) {
            log.error("Failed to submit backtest to engine: backtestId={}", backtestId, e);
            // 이벤트 발행으로 상태 업데이트 처리
            eventPublisher.publishEvent(new com.stockone19.backend.backtest.event.BacktestFailureEvent(backtestId, e.getMessage()));
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 백테스트 엔진 요청 생성
     */
    public BacktestExecutionRequest createBacktestEngineRequest(Backtest backtest) {
        try {
            // 포트폴리오 조회
            List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(backtest.getPortfolioId());
            
            // BacktestExecutionRequest.of() 메서드 사용
            return BacktestExecutionRequest.of(
                backtest.getId(),
                backtest.getStartAt(),
                backtest.getEndAt(),
                holdings,
                callbackBaseUrl + "/api/backtests/callback"
            );

        } catch (Exception e) {
            log.error("Failed to create backtest engine request for backtestId: {}", backtest.getId(), e);
            throw new RuntimeException("Failed to create backtest engine request", e);
        }
    }
}
