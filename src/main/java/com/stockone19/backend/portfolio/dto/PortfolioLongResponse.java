package com.stockone19.backend.portfolio.dto;

import java.util.List;

public record PortfolioLongResponse(
        Long portfolioId,
        List<HoldingDetail> holdings,
        String ruleId
) {

    public record HoldingDetail(
            String name,
            double weight,
            double value,
            double dailyRate
    ) {}
}
