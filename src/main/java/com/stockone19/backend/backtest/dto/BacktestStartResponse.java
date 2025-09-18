package com.stockone19.backend.backtest.dto;

/**
 * 백테스트 엔진에서 받을 시작 응답 DTO
 */
public record BacktestStartResponse(
    String jobId,
    String status // "started"
) {}
