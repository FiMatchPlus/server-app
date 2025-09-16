package com.stockone19.backend.stock.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public record StockPriceResponse(
        String status,
        String message,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        MarketStatus marketStatus,
        List<StockPriceData> data
) {

    public static StockPriceResponse success(String message, List<StockPriceData> data) {
        return new StockPriceResponse(
                "success",
                message,
                Instant.now(),
                MarketStatus.current(),
                data
        );
    }

    public record MarketStatus(
            boolean isOpen,
            String session,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Instant nextClose
    ) {
        public static MarketStatus current() {
            return new MarketStatus(
                    com.stockone19.backend.common.util.DateTimeUtil.isMarketOpen(),
                    "regular_trading",
                    Instant.parse(com.stockone19.backend.common.util.DateTimeUtil.getNextCloseTime() + "Z")
            );
        }
    }

    public record StockPriceData(
            String ticker,
            String name,
            double currentPrice,
            double previousClose,
            double dailyRate,
            double dailyChange,
            double marketCap
    ) {}
}

