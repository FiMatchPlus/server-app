package com.stockone19.backend.stock.dto;

public record StockSearchResult(
        String ticker,
        String name,
        String industryName
) {
    public static StockSearchResult of(String ticker, String name, String industryName) {
        return new StockSearchResult(ticker, name, industryName);
    }
}
