package com.stockone19.backend.product.controller;

import com.stockone19.backend.common.dto.ApiResponse;
import com.stockone19.backend.product.dto.ProductListResponse;
import com.stockone19.backend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    /**
     * 상품 목록 조회
     * <ul>
     *     <li>모든 상품의 목록을 조회합니다</li>
     *     <li>각 상품의 기본 정보(id, name, riskLevel, keywords, oneYearReturn, minInvestment)를 반환합니다</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<ProductListResponse> getAllProducts() {
        log.info("GET /api/products - 상품 목록 조회");

        ProductListResponse response = productService.getAllProducts();
        return ApiResponse.success("상품 목록을 조회했습니다", response);
    }
}

