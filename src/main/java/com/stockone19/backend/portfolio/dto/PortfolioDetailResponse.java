package com.stockone19.backend.portfolio.dto;

import java.util.List;

public record PortfolioDetailResponse(
        Long portfolioId,
        
        String name,

        Double totalValue,

        String description,

        List<HoldingResponse> holdings
) {

    public record HoldingResponse(
            String symbol,

            String name,

            Integer shares,

            Double currentPrice,

            Double totalValue,

            Double change,
            
            Double changePercent,

            Double weight
    ) {}
}

