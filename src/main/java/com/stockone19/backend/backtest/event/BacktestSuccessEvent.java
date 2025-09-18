package com.stockone19.backend.backtest.event;

import com.stockone19.backend.backtest.dto.BacktestCallbackResponse;

/**
 * 백테스트 성공 이벤트
 */
public record BacktestSuccessEvent(
    Long backtestId,
    BacktestCallbackResponse callback
) {}
