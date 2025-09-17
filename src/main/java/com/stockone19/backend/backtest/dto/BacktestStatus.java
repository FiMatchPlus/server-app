package com.stockone19.backend.backtest.dto;

/**
 * 백테스트 실행 상태
 */
public enum BacktestStatus {
    CREATED,    // 생성됨
    RUNNING,    // 실행 중
    COMPLETED,  // 완료됨
    FAILED      // 실패함
}
