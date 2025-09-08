package com.stockone19.backend.stock.domain;

import lombok.Getter;

@Getter
public enum StockType {
    EF("ETF"),
    ST("주식");

    private final String description;

    StockType(String description) {
        this.description = description;
    }

}
