package com.fimatchplus.backend.backtest.event;

import com.fimatchplus.backend.backtest.dto.BacktestCallbackResponse;

/**
 * 백테스트 성공 이벤트
 */
public record BacktestSuccessEvent(
    Long backtestId,
    BacktestCallbackResponse callback
) {}
