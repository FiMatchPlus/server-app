package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 백테스트 조회 응답
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BacktestResponse(
        Long id,                    // 백테스트 고유 ID
        String name,                // 백테스트 이름
        String period,              // 백테스트 기간
        Long executionTime,         // 실행 시간 (밀리초)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,    // 생성일시
        BacktestStatus status       // 실행 상태
) {
    
    /**
     * 백테스트 응답 생성
     */
    public static BacktestResponse of(
            Long id,
            String name,
            String period,
            Long executionTime,
            LocalDateTime createdAt,
            BacktestStatus status
    ) {
        return new BacktestResponse(
                id, name, period, executionTime, createdAt, status
        );
    }
}
