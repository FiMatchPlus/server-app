package com.stockone19.backend.backtest.dto;

/**
 * 백테스트 엔진에서 콜백으로 받을 응답 DTO
 */
public record BacktestCallbackResponse(
    String jobId,
    Boolean success,
    Object result, // 성공 시 백테스트 결과
    String errorMessage // 실패 시 에러 메시지
) {}
