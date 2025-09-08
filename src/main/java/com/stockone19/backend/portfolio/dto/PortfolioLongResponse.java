package com.stockone19.backend.portfolio.dto;

import java.util.List;

public record PortfolioLongResponse(
        Long portfolioId,
        List<HoldingDetail> holdings,
        String ruleId
) {

    public static ApiResponse<PortfolioLongResponse> success(String message, PortfolioLongResponse data) {
        return ApiResponse.success(message, data);
    }

    public record HoldingDetail(
            String name,
            double weight,
            double value,
            double dailyRate
    ) {}
}
