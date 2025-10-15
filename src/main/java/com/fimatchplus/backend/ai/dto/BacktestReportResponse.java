package com.fimatchplus.backend.ai.dto;

/**
 * 백테스트 레포트 생성 응답 DTO
 */
public record BacktestReportResponse(
        Long backtestId,
        String report
) {
    public static BacktestReportResponse of(Long backtestId, String report) {
        return new BacktestReportResponse(backtestId, report);
    }
}
