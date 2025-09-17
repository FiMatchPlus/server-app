package com.stockone19.backend.stock.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "stock_prices")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StockPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(nullable = false)
    private LocalDateTime datetime;

    @Column(name = "interval_unit", nullable = false)
    private String intervalUnit;

    @Column(name = "open_price", precision = 15, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 15, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 15, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "close_price", precision = 15, scale = 2)
    private BigDecimal closePrice;

    @Column
    private Long volume;

    @Column(name = "change_amount", precision = 15, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "change_rate", precision = 15, scale = 2)
    private BigDecimal changeRate;

    public StockPrice(String stockCode, LocalDateTime datetime, String intervalUnit,
                      BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
                      BigDecimal closePrice, Long volume, BigDecimal changeAmount,
                      BigDecimal changeRate) {
        this.stockCode = stockCode;
        this.datetime = datetime;
        this.intervalUnit = intervalUnit;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.changeAmount = changeAmount;
        this.changeRate = changeRate;
    }

    public static StockPrice of(
            Long id,
            String stockCode,
            LocalDateTime datetime,
            String intervalUnit,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            Long volume,
            BigDecimal changeAmount,
            BigDecimal changeRate
    ) {
        StockPrice stockPrice = new StockPrice(stockCode, datetime, intervalUnit,
                openPrice, highPrice, lowPrice, closePrice, volume,
                changeAmount, changeRate);
        stockPrice.id = id;  // ID는 직접 설정 (데이터베이스에서 조회시)
        return stockPrice;
    }
}


