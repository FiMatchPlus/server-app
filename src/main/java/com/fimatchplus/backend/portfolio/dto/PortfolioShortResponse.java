package com.fimatchplus.backend.portfolio.dto;

import com.fimatchplus.backend.common.dto.ApiResponse;

import java.util.List;

public record PortfolioShortResponse(
        String name,
        double totalValue,
        List<HoldingSummary> holdings,
        double dailySum
) {

    public static ApiResponse<PortfolioShortResponse> success(String message, PortfolioShortResponse data) {
        return ApiResponse.success(message, data);
    }

    public record HoldingSummary(
            String name,
            double weight,
            double dailyRate
    ) {
    }
}

