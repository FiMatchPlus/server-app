package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.dto.BacktestExecutionRequest;
import com.stockone19.backend.backtest.dto.BacktestExecutionResponse;
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
    
    @Value("${backtest.server.url:http://data-server:8000}")
    private String backtestServerUrl;

    public BacktestServerClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
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
                                .map(body -> {
                                    log.error("Backtest server error: status={}, body={}", response.statusCode(), body);
                                    return new RuntimeException("Backtest server error: " + response.statusCode() + " - " + body);
                                }))
                .bodyToMono(BacktestExecutionResponse.class)
                .timeout(Duration.ofMinutes(10)) // 10분 타임아웃
                .doOnSuccess(response -> log.info("Backtest execution completed successfully"))
                .doOnError(error -> log.error("Backtest execution failed: {}", error.getMessage()));
    }
}
