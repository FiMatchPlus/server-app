package com.stockone19.backend.product.dto;

import com.stockone19.backend.product.domain.Product;
import com.stockone19.backend.product.domain.RiskLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record ProductSummary(
        String id,
        String name,
        RiskLevel riskLevel,
        List<String> keywords,
        BigDecimal oneYearReturn,
        Long minInvestment
) {
    public static ProductSummary from(Product product) {
        return ProductSummary.builder()
                .id(String.valueOf(product.getId()))
                .name(product.getName())
                .riskLevel(product.getRiskLevel())
                .keywords(List.of(product.getKeywords()))
                .oneYearReturn(product.getOneYearReturn())
                .minInvestment(product.getMinInvestment())
                .build();
    }
}

