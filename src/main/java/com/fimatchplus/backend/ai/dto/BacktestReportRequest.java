package com.fimatchplus.backend.ai.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 백테스트 레포트 생성 요청 DTO
 */
public record BacktestReportRequest(
        @NotNull(message = "백테스트 ID는 필수입니다")
        Long backtestId,
        
        String analysisFocus
) {
}
