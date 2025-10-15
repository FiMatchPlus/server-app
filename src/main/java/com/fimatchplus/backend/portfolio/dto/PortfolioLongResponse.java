package com.fimatchplus.backend.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PortfolioLongResponse(
        Long portfolioId,
        String name,
        String description,
        List<HoldingDetail> holdings,
        String ruleId,
        RulesDetail rules,
        AnalysisDetail analysis
) {

    public record HoldingDetail(
            String ticker,
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
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            LocalDateTime createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
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

    public record AnalysisDetail(
            String status,
            List<AnalysisResult> results
    ) {}

    public record AnalysisResult(
            String type,
            String riskLevel,
            Map<String, Double> holdings
    ) {}

}
