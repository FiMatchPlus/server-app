package com.fimatchplus.backend.portfolio.dto;

import com.fimatchplus.backend.common.dto.ApiResponse;

public record PortfolioSummaryResponse(
        double totalAssets,
        double dailyTotalReturn,
        double dailyTotalChange
) {

    public static ApiResponse<PortfolioSummaryResponse> success(String message, PortfolioSummaryResponse data) {
        return ApiResponse.success(message, data);
    }
}