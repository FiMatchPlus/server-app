package com.fimatchplus.backend.stock.dto;

import java.util.List;

public record StockSearchResponse(
        List<StockSearchData> results,
        int total
) {

    public static StockSearchResponse success(List<StockSearchData> results) {
        return new StockSearchResponse(results, results.size());
    }

    public record StockSearchData(
            String symbol,
            String name,
            String sector
    ) {
        public static StockSearchData of(String symbol, String name, String sector) {
            return new StockSearchData(symbol, name, sector);
        }
    }
}

