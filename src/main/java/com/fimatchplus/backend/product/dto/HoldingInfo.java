package com.fimatchplus.backend.product.dto;

import com.fimatchplus.backend.product.domain.ProductHolding;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record HoldingInfo(
        String symbol,
        String name,
        BigDecimal weight,
        String sector
) {
    public static HoldingInfo from(ProductHolding holding) {
        return HoldingInfo.builder()
                .symbol(holding.getSymbol())
                .name(holding.getName())
                .weight(holding.getWeight())
                .sector(holding.getSector())
                .build();
    }
}

