package com.fimatchplus.backend.backtest.dto;

import java.time.LocalDateTime;

public record CreateBacktestResult(
        Long backtestId,
        String title,
        String description,
        String ruleId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String benchmarkCode
) {
}
