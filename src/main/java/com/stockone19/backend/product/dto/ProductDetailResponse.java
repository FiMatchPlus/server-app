package com.stockone19.backend.product.dto;

import com.stockone19.backend.product.domain.Product;
import com.stockone19.backend.product.domain.RiskLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record ProductDetailResponse(
        String id,
        String name,
        String description,
        RiskLevel riskLevel,
        
        // 성과 지표
        BigDecimal volatilityIndex,
        BigDecimal oneYearReturn,
        BigDecimal mdd,
        BigDecimal sharpeRatio,
        
        // 메타 정보
        List<String> keywords,
        Long minInvestment,
        
        // 보유 종목 구성
        List<HoldingInfo> holdings
) {
    public static ProductDetailResponse from(Product product) {
        return ProductDetailResponse.builder()
                .id(String.valueOf(product.getId()))
                .name(product.getName())
                .description(product.getDescription())
                .riskLevel(product.getRiskLevel())
                .volatilityIndex(product.getVolatilityIndex())
                .oneYearReturn(product.getOneYearReturn())
                .mdd(product.getMdd())
                .sharpeRatio(product.getSharpeRatio())
                .keywords(List.of(product.getKeywords()))
                .minInvestment(product.getMinInvestment())
                .holdings(product.getHoldings().stream()
                        .map(HoldingInfo::from)
                        .toList())
                .build();
    }
}

