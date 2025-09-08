package com.stockone19.backend.stock.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.stockone19.backend.common.util.DateTimeUtil;

import java.time.Instant;
import java.util.List;

public record StockDetailResponse(
        String status,
        String message,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        MarketStatus marketStatus,
        StockDetailData data
) {

    public static StockDetailResponse success(String message, StockDetailData data) {
        return new StockDetailResponse(
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
                    DateTimeUtil.isMarketOpen(),
                    "regular_trading",
                    Instant.parse(DateTimeUtil.getNextCloseTime() + "Z")
            );
        }
    }

    public record StockDetailData(
            String ticker,
            String name,
            String engName,
            String exchange,
            String industryName,
            List<ChartData> chartData,
            SummaryData summaryData
    ) {}

    public record ChartData(
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Instant timestamp,
            double open,
            double close,
            double high,
            double low,
            long volume
    ) {}

    public record SummaryData(
            String ticker,
            String name,
            double currentPrice,
            double previousClose,
            double dailyRate,
            double dailyChange,
            long volume,
            double marketCap
    ) {}
}
