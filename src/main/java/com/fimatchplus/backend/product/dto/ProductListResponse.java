package com.fimatchplus.backend.product.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ProductListResponse(
        List<ProductSummary> products
) {
    public static ProductListResponse of(List<ProductSummary> products) {
        return ProductListResponse.builder()
                .products(products)
                .build();
    }
}

