package com.stockone19.backend.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreatePortfolioRequest(
        @NotBlank(message = "포트폴리오 이름은 필수입니다")
        String name,

        @NotNull(message = "총 자산은 필수입니다")
        @Positive(message = "총 자산은 양수여야 합니다")
        Double totalValue,

        String description,

        @NotNull(message = "보유 종목은 필수입니다")
        List<HoldingRequest> holdings,

        @NotNull(message = "규칙은 필수입니다")
        RulesRequest rules
) {

    public record HoldingRequest(
            @NotBlank(message = "종목 코드는 필수입니다")
            String symbol,

            @NotBlank(message = "종목 이름은 필수입니다")
            String name,

            @NotNull(message = "보유 수량은 필수입니다")
            @Positive(message = "보유 수량은 양수여야 합니다")
            Integer shares,

            @NotNull(message = "현재가는 필수입니다")
            @Positive(message = "현재가는 양수여야 합니다")
            Double currentPrice,

            @NotNull(message = "총 가치는 필수입니다")
            @Positive(message = "총 가치는 양수여야 합니다")
            Double totalValue,

            Double change,
            Double changePercent,

            @NotNull(message = "비중은 필수입니다")
            @Positive(message = "비중은 양수여야 합니다")
            Double weight
    ) {}

    public record RulesRequest(
            String memo,
            List<RuleItemRequest> rebalance,
            List<RuleItemRequest> stopLoss,
            List<RuleItemRequest> takeProfit
    ) {}

    public record RuleItemRequest(
            String category,
            String threshold,
            String description
    ) {}
}
