package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockone19.backend.portfolio.domain.Holding;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백테스트 엔진으로 보낼 비동기 요청 DTO
 */
public record BacktestAsyncRequest(
    LocalDateTime start,
    LocalDateTime end,
    List<HoldingRequest> holdings,
    @JsonProperty("rebalance_frequency")
    String rebalanceFrequency, // "daily"
    @JsonProperty("callback_url")
    String callbackUrl // 콜백 받을 URL
) {
    public static BacktestAsyncRequest of(LocalDateTime start, LocalDateTime end, 
                                        List<Holding> holdings, String callbackUrl) {
        List<HoldingRequest> holdingRequests = holdings.stream()
                .map(holding -> new HoldingRequest(holding.symbol(), holding.shares()))
                .toList();
        return new BacktestAsyncRequest(start, end, holdingRequests, "daily", callbackUrl);
    }
    
    public record HoldingRequest(
        String code,
        int quantity
    ) {}
}
