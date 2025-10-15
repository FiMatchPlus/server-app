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
import com.stockone19.backend.backtest.repository.BacktestRuleRepository;
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
    private final BacktestRuleRepository backtestRuleRepository;
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

            BacktestExecutionRequest request = createBacktestEngineRequest(backtest);

            try {
                String requestBody = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(request);
                log.info("Sending backtest request to engine - backtestId: {}\nRequest Body:\n{}", 
                        backtestId, requestBody);
            } catch (Exception e) {
                log.warn("Failed to serialize request body for logging: {}", e.getMessage());
            }

            BacktestStartResponse response = backtestEngineWebClient
                .post()
                .uri("/backtest/start")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(BacktestStartResponse.class)
                .block();

            jobMappingService.saveMapping(response.jobId(), backtestId);

            log.info("Backtest submitted to engine: backtestId={}, jobId={}", 
                    backtestId, response.jobId());

        } catch (Exception e) {
            log.error("Failed to submit backtest to engine: backtestId={}", backtestId, e);
            eventPublisher.publishEvent(new com.stockone19.backend.backtest.event.BacktestFailureEvent(backtestId, e.getMessage()));
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 백테스트 엔진 요청 생성
     */
    public BacktestExecutionRequest createBacktestEngineRequest(Backtest backtest) {
        try {
            List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(backtest.getPortfolioId());
            
            BacktestExecutionRequest.RulesRequest rules = convertToEngineRules(backtest.getRuleId());
            
            return BacktestExecutionRequest.of(
                backtest.getId(),
                backtest.getStartAt(),
                backtest.getEndAt(),
                holdings,
                callbackBaseUrl + "/backtests/callback",
                rules,
                backtest.getBenchmarkCode()
            );

        } catch (Exception e) {
            log.error("Failed to create backtest engine request for backtestId: {}", backtest.getId(), e);
            throw new RuntimeException("Failed to create backtest engine request", e);
        }
    }

    /**
     * MongoDB BacktestRuleDocument를 백테스트 엔진 형식으로 변환
     */
    private BacktestExecutionRequest.RulesRequest convertToEngineRules(String ruleId) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            log.debug("Rule ID is null or empty, returning null rules");
            return null;
        }

        try {
            BacktestRuleDocument backtestRules = backtestRuleRepository.findById(ruleId).orElse(null);
            if (backtestRules == null) {
                log.warn("Backtest rule not found for id: {}, returning null rules", ruleId);
                return null;
            }

            List<BacktestExecutionRequest.RuleItem> stopLossItems = convertRuleItems(backtestRules.getStopLoss());
            
            List<BacktestExecutionRequest.RuleItem> takeProfitItems = convertRuleItems(backtestRules.getTakeProfit());

            return new BacktestExecutionRequest.RulesRequest(stopLossItems, takeProfitItems);

        } catch (Exception e) {
            log.warn("Failed to convert rules for id: {}, returning null rules", ruleId, e);
            return null;
        }
    }

    /**
     * MongoDB BacktestRuleDocument.RuleItem을 백테스트 엔진 RuleItem으로 변환
     * threshold는 비율(0~1 사이의 값)
     */
    private List<BacktestExecutionRequest.RuleItem> convertRuleItems(List<BacktestRuleDocument.RuleItem> mongoRuleItems) {
        if (mongoRuleItems == null || mongoRuleItems.isEmpty()) {
            return List.of();
        }

        return mongoRuleItems.stream()
                .map(item -> {
                    try {
                        Double value = Double.parseDouble(item.getThreshold());
                        log.debug("Converting rule item: category={}, threshold={}, value={}", 
                                 item.getCategory(), item.getThreshold(), value);
                        return new BacktestExecutionRequest.RuleItem(item.getCategory(), value);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse threshold value: {} for category: {}", 
                                item.getThreshold(), item.getCategory());
                        return null;
                    }
                })
                .filter(item -> item != null)
                .toList();
    }
}
