package com.stockone19.backend.stock.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.stockone19.backend.stock.domain.PriceChangeSign;

import java.time.Instant;
import java.util.List;

public record StockPriceResponse(
        MarketStatus marketStatus,
        List<StockPriceData> data
) {

    public static StockPriceResponse success(List<StockPriceData> data) {
        return new StockPriceResponse(
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
            double dailyRate,
            double dailyChange,
            PriceChangeSign sign
    ) {}
}

