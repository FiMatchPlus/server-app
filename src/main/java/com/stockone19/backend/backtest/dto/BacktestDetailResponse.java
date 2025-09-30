package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.stockone19.backend.portfolio.domain.Rules;

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
        Double executionTime,   // 실행 시간 (초)
        String benchmarkCode,   // 벤치마크 코드
        String benchmarkName,  // 벤치마크 이름
        BacktestMetrics metrics,    // 백테스트 성과 지표
        List<DailyEquityData> dailyEquity,  // 일별 평가액 데이터
        List<BenchmarkData> benchmarkData,  // 벤치마크 일별 데이터
        List<HoldingData> holdings,         // 포트폴리오 보유 정보
        String report,         // 마크다운 형식의 전체 레포트
        Rules rule             // 매매 규칙 정보 (null일 수 있음)
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
     * 벤치마크 일별 데이터
     */
    public record BenchmarkData(
            @JsonFormat(pattern = "yyyy-MM-dd")
            String date,        // 날짜
            Double value,       // 벤치마크 지수 값
            Double dailyReturn // 일일 수익률
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
            Double executionTime,
            String benchmarkCode,
            String benchmarkName,
            BacktestMetrics metrics,
            List<DailyEquityData> dailyEquity,
            List<BenchmarkData> benchmarkData,
            List<HoldingData> holdings,
            String report,
            Rules rule
    ) {
        return new BacktestDetailResponse(
                historyId, name, period, executionTime, benchmarkCode, benchmarkName,
                metrics, dailyEquity, benchmarkData, holdings, report, rule
        );
    }
}
