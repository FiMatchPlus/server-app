package com.stockone19.backend.portfolio.dto;

import java.util.List;

public record PortfolioListResponse(
        List<PortfolioListItem> portfolios
) {

    public record PortfolioListItem(
            String name,
            String description,
            List<HoldingStock> holdingStocks,
            double totalAssets,
            double dailyRate,
            double dailyChange
    ) {}

    public record HoldingStock(
            String ticker,
            String name,
            double weight
    ) {}
}













