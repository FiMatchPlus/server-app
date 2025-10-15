package com.fimatchplus.backend.backtest.exception;

import com.fimatchplus.backend.backtest.dto.BacktestServerErrorResponse;
import lombok.Getter;

/**
 * 백테스트 실행 중 발생하는 예외
 */
@Getter
public class BacktestExecutionException extends RuntimeException {
    
    private final BacktestServerErrorResponse errorResponse;
    
    public BacktestExecutionException(BacktestServerErrorResponse errorResponse) {
        super(errorResponse.error().message());
        this.errorResponse = errorResponse;
    }
    
    public BacktestExecutionException(String message) {
        super(message);
        this.errorResponse = null;
    }
    
    public BacktestExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.errorResponse = null;
    }
    
    /**
     * 구조화된 에러 응답이 있는지 확인
     */
    public boolean hasStructuredError() {
        return errorResponse != null;
    }
    
    /**
     * 에러 타입 반환
     */
    public String getErrorType() {
        return errorResponse != null ? errorResponse.error().errorType() : "UNKNOWN_ERROR";
    }
}
