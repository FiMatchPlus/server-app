package com.stockone19.backend.stock.dto;

import java.math.BigDecimal;

public record StockSearchResult(
        String ticker,
        String name,
        String industryName,
        BigDecimal price,
        BigDecimal changePercent
) {
    public static StockSearchResult of(String ticker, String name, String industryName, BigDecimal price, BigDecimal changePercent) {
        return new StockSearchResult(ticker, name, industryName, price, changePercent);
    }
}
