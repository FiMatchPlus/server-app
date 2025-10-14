package com.stockone19.backend.product.service;

import com.stockone19.backend.product.dto.ProductListResponse;
import com.stockone19.backend.product.dto.ProductSummary;
import com.stockone19.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductListResponse getAllProducts() {
        List<ProductSummary> products = productRepository.findAll()
                .stream()
                .map(ProductSummary::from)
                .toList();

        log.info("Retrieved {} products", products.size());
        return ProductListResponse.of(products);
    }
}

