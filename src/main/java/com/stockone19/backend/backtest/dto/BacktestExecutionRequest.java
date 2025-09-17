package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 외부 백테스트 서버로 전송할 요청 DTO
 */
@Builder
public record BacktestExecutionRequest(
        LocalDateTime start,
        LocalDateTime end,
        List<HoldingRequest> holdings,
        @JsonProperty("rebalance_frequency")
        String rebalanceFrequency
) {
    
    @Builder
    public record HoldingRequest(
            String code,
            int quantity
    ) {}
}
