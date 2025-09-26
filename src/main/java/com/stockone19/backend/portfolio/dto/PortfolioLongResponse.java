package com.stockone19.backend.portfolio.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PortfolioLongResponse(
        Long portfolioId,
        List<HoldingDetail> holdings,
        String ruleId,
        RulesDetail rules
) {

    public record HoldingDetail(
            String name,
            double weight,
            double value,
            double dailyRate
    ) {}

    public record RulesDetail(
            String id,
            String memo,
            String basicBenchmark,
            BenchmarkDetail benchmark, 
            List<RuleItemDetail> rebalance,
            List<RuleItemDetail> stopLoss,
            List<RuleItemDetail> takeProfit,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record BenchmarkDetail(
            String code,
            String name,
            String description
    ) {}

    public record RuleItemDetail(
            String category,
            String threshold,
            String description
    ) {}
}
