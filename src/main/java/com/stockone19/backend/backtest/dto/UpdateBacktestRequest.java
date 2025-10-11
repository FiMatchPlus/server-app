package com.stockone19.backend.backtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateBacktestRequest(
        @NotBlank(message = "백테스트 제목은 필수입니다")
        String title,

        String description,

        @NotNull(message = "시작일은 필수입니다")
        LocalDateTime startAt,

        @NotNull(message = "종료일은 필수입니다")
        LocalDateTime endAt,

        RulesRequest rules,
        
        @NotNull(message = "벤치마크 지수는 필수입니다")
        String benchmarkCode
) {

    public record RulesRequest(
            String memo,
            List<RuleItemRequest> stopLoss,
            List<RuleItemRequest> takeProfit
    ) {}

    public record RuleItemRequest(
            String category,
            String threshold,
            String description
    ) {}
}

