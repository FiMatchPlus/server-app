package com.stockone19.backend.backtest.domain;

/**
 * 백테스트 결과 상태
 */
public enum ResultStatus {
    PENDING,    // JPA 저장 완료, JDBC 대기 중
    COMPLETED,  // 모든 저장 완료
    LIQUIDATED, // 청산됨
    FAILED      // 저장 실패
}
