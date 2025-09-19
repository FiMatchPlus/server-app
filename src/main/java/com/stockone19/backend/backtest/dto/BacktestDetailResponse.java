package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 백테스트 상세 조회 응답
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BacktestDetailResponse(
        String historyId,       // portfolio_snapshot_id
        String name,            // 백테스트 이름
        String period,          // 백테스트 기간
        Long executionTime,     // 실행 시간 (밀리초)
        BacktestMetrics metrics,    // 백테스트 성과 지표
        List<DailyEquityData> dailyEquity,  // 일별 평가액 데이터
        List<HoldingData> holdings          // 포트폴리오 보유 정보
) {
    
    /**
     * 일별 평가액 데이터
     */
    public record DailyEquityData(
            @JsonFormat(pattern = "yyyy-MM-dd")
            String date,
            Map<String, Double> stocks  // 각 주식의 평가액 (주식명 -> 평가액)
    ) {}
    
    /**
     * 포트폴리오 보유 정보
     */
    public record HoldingData(
            String stockName,   // 주식명
            Integer quantity    // 보유 수량 (주)
    ) {}
    
    /**
     * 백테스트 상세 응답 생성
     */
    public static BacktestDetailResponse of(
            String historyId,
            String name,
            String period,
            Long executionTime,
            BacktestMetrics metrics,
            List<DailyEquityData> dailyEquity,
            List<HoldingData> holdings
    ) {
        return new BacktestDetailResponse(
                historyId, name, period, executionTime, metrics, dailyEquity, holdings
        );
    }
}
