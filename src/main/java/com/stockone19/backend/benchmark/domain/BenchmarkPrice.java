package com.stockone19.backend.benchmark.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 벤치마크 지수 가격 정보 엔티티
 * benchmark_prices 테이블과 매핑
 */
@Getter
@Entity
@Table(name = "benchmark_prices")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BenchmarkPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "index_code", nullable = false, length = 20)
    private String indexCode;

    @Column(nullable = false)
    private LocalDateTime datetime;

    @Column(name = "open_price", nullable = false)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false)
    private BigDecimal closePrice;

    @Column(name = "change_amount", nullable = false)
    private BigDecimal changeAmount;

    @Column(name = "change_rate", nullable = false)
    private BigDecimal changeRate;

    @Column
    private Long volume = 0L;

    @Column(name = "trading_value")
    private BigDecimal tradingValue = BigDecimal.ZERO;

    @Column(name = "market_cap")
    private BigDecimal marketCap;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 생성자
    public BenchmarkPrice(String indexCode, LocalDateTime datetime, 
                         BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
                         BigDecimal changeAmount, BigDecimal changeRate, Long volume, 
                         BigDecimal tradingValue, BigDecimal marketCap) {
        this.indexCode = indexCode;
        this.datetime = datetime;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.changeAmount = changeAmount;
        this.changeRate = changeRate;
        this.volume = volume != null ? volume : 0L;
        this.tradingValue = tradingValue != null ? tradingValue : BigDecimal.ZERO;
        this.marketCap = marketCap;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
