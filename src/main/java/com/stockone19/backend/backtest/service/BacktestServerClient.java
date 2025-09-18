package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.dto.BacktestExecutionRequest;
import com.stockone19.backend.backtest.dto.BacktestExecutionResponse;
import com.stockone19.backend.backtest.dto.BacktestServerErrorResponse;
import com.stockone19.backend.backtest.exception.BacktestExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 외부 백테스트 서버와 통신하는 클라이언트
 */
@Slf4j
@Component
public class BacktestServerClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${backtest.server.url:http://data-server:8000}")
    private String backtestServerUrl;

    public BacktestServerClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 외부 백테스트 서버에 백테스트 실행 요청
     *
     * @param request 백테스트 실행 요청
     * @return 백테스트 실행 결과
     */
    public Mono<BacktestExecutionResponse> executeBacktest(BacktestExecutionRequest request) {
        log.info("Executing backtest on external server: {}", backtestServerUrl);
        
        return webClient
                .post()
                .uri(backtestServerUrl + "/backtest/run")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("Unknown error")
                                .flatMap(body -> {
                                    log.error("Backtest server error: status={}, body={}", response.statusCode(), body);
                                    return parseErrorResponse(body, response.statusCode().value());
                                }))
                .bodyToMono(BacktestExecutionResponse.class)
                .timeout(Duration.ofMinutes(10)) // 10분 타임아웃
                .doOnSuccess(response -> log.info("Backtest execution completed successfully"))
                .doOnError(error -> log.error("Backtest execution failed: {}", error.getMessage()));
    }
    
    /**
     * 에러 응답을 파싱하여 적절한 예외로 변환
     */
    private Mono<RuntimeException> parseErrorResponse(String errorBody, int statusCode) {
        try {
            // JSON 응답인지 확인하고 파싱 시도
            if (errorBody.trim().startsWith("{")) {
                BacktestServerErrorResponse errorResponse = objectMapper.readValue(errorBody, BacktestServerErrorResponse.class);
                return Mono.just(new BacktestExecutionException(errorResponse));
            } else {
                // JSON이 아닌 경우 일반 에러로 처리
                return Mono.just(new RuntimeException("Backtest server error: " + statusCode + " - " + errorBody));
            }
        } catch (Exception e) {
            log.warn("Failed to parse error response as JSON: {}", e.getMessage());
            return Mono.just(new RuntimeException("Backtest server error: " + statusCode + " - " + errorBody));
        }
    }
}
