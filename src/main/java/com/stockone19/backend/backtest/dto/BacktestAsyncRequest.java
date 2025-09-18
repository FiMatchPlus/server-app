package com.stockone19.backend.backtest.dto;

import com.stockone19.backend.portfolio.domain.Holding;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백테스트 엔진으로 보낼 비동기 요청 DTO
 */
public record BacktestAsyncRequest(
    LocalDateTime start,
    LocalDateTime end,
    List<Holding> holdings,
    String rebalanceFrequency, // "daily"
    String callbackUrl // 콜백 받을 URL
) {
    public static BacktestAsyncRequest of(LocalDateTime start, LocalDateTime end, 
                                        List<Holding> holdings, String callbackUrl) {
        return new BacktestAsyncRequest(start, end, holdings, "daily", callbackUrl);
    }
}
