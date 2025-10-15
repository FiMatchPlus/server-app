package com.stockone19.backend.stock.domain;

import com.stockone19.backend.stock.converter.BooleanToYNConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "stocks")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Column(name = "eng_name")
    private String engName;

    private String isin;

    private String region;

    @Column
    private String currency;

    @Column(name = "major_code")
    private String majorCode;

    @Column(name = "medium_code")
    private String mediumCode;

    @Column(name = "minor_code")
    private String minorCode;

    private String exchange;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "industry_code")
    private Integer industryCode;

    @Column(name = "industry_name")
    private String industryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockType type;

    public Stock(String ticker, String name, String engName, String isin, String region,
                 String currency, String majorCode, String mediumCode, String minorCode,
                 String exchange, boolean isActive, Integer industryCode, 
                 String industryName, StockType type) {
        this.ticker = ticker;
        this.name = name;
        this.engName = engName;
        this.isin = isin;
        this.region = region;
        this.currency = currency;
        this.majorCode = majorCode;
        this.mediumCode = mediumCode;
        this.minorCode = minorCode;
        this.exchange = exchange;
        this.isActive = isActive;
        this.industryCode = industryCode;
        this.industryName = industryName;
        this.type = type;
    }

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
        Stock stock = new Stock(ticker, name, engName, isin, region, currency,
                majorCode, mediumCode, minorCode, exchange, isActive,
                industryCode, industryName, type);
        stock.id = id;
        return stock;
    }
}

