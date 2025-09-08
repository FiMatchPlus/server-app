package com.stockone19.backend.stock.domain;

public record Stock(
        Long id,
        String ticker,
        String name,
        String engName,
        String isin,
        String region,
        String currency,
        String majorCode,
        String mediumCode,
        String minorCode,
        String exchange,
        boolean isActive,
        Integer industryCode,
        String industryName,
        StockType type
) {

    public static Stock of(
            Long id,
            String ticker,
            String name,
            String engName,
            String isin,
            String region,
            String currency,
            String majorCode,
            String mediumCode,
            String minorCode,
            String exchange,
            boolean isActive,
            Integer industryCode,
            String industryName,
            StockType type
    ) {
        return new Stock(
                id, ticker, name, engName, isin, region, currency,
                majorCode, mediumCode, minorCode, exchange, isActive,
                industryCode, industryName, type
        );
    }
}

