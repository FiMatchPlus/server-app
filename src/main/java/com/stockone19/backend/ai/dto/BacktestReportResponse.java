package com.stockone19.backend.ai.dto;

/**
 * 백테스트 레포트 생성 응답 DTO
 */
public record BacktestReportResponse(
        Long backtestId,
        String report  // AI가 생성한 분석 리포트 내용 (String)
) {
    public static BacktestReportResponse of(Long backtestId, String report) {
        return new BacktestReportResponse(backtestId, report);
    }
}
