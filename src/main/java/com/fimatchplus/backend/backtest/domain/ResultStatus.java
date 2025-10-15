package com.fimatchplus.backend.backtest.domain;

/**
 * 백테스트 결과 상태
 */
public enum ResultStatus {
    PENDING,
    COMPLETED,
    LIQUIDATED,
    FAILED
}
