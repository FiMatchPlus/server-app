package com.stockone19.backend.backtest.event;

/**
 * 백테스트 실패 이벤트
 */
public record BacktestFailureEvent(
    Long backtestId,
    String errorMessage
) {}
