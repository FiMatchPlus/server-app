package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 백테스트 엔진에서 받을 시작 응답 DTO
 */
public record BacktestStartResponse(
    @JsonProperty("job_id")
    String jobId,
    String status,
    String message
) {}
