package com.fimatchplus.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fimatchplus.backend.backtest.service.BacktestRuleDocument;

import java.util.List;
import java.util.Map;

/**
 * 백테스트 상세 조회 응답
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BacktestDetailResponse(
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
        BacktestRuleDocument rules
) {
    
    /**
     * 일별 평가액 데이터
     */
    public record DailyEquityData(
            @JsonFormat(pattern = "yyyy-MM-dd")
            String date,
            Map<String, Double> stocks
    ) {}
    
    /**
     * 벤치마크 일별 데이터
     */
    public record BenchmarkData(
            @JsonFormat(pattern = "yyyy-MM-dd")
            String date,
            Double value,
            Double dailyReturn
    ) {}
    
    /**
     * 포트폴리오 보유 정보
     */
    public record HoldingData(
            String stockName,
            Integer quantity
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
            BacktestRuleDocument rules
    ) {
        return new BacktestDetailResponse(
                historyId, name, period, executionTime, benchmarkCode, benchmarkName,
                metrics, dailyEquity, benchmarkData, holdings, report, rules
        );
    }
}
