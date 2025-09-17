package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        BacktestStatus status,      // 실행 상태
        BacktestMetrics metrics,    // 완료된 경우 결과 지표 (선택사항)
        List<DailyReturn> dailyReturns  // 완료된 경우 일별 수익률 (선택사항)
) {
    
    /**
     * 일별 수익률 데이터
     */
    public record DailyReturn(
            @JsonFormat(pattern = "yyyy-MM-dd")
            String date,
            Map<String, Object> data    // 추가 데이터 (수익률, 누적수익률 등)
    ) {}
    
    /**
     * 기본 백테스트 응답 생성 (진행 중 또는 생성됨 상태)
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
                id, name, period, executionTime, createdAt, status, null, null
        );
    }
    
    /**
     * 완료된 백테스트 응답 생성 (지표와 일별 수익률 포함)
     */
    public static BacktestResponse ofCompleted(
            Long id,
            String name,
            String period,
            Long executionTime,
            LocalDateTime createdAt,
            BacktestStatus status,
            BacktestMetrics metrics,
            List<DailyReturn> dailyReturns
    ) {
        return new BacktestResponse(
                id, name, period, executionTime, createdAt, status, metrics, dailyReturns
        );
    }
}
