package com.stockone19.backend.product.repository;

import com.stockone19.backend.product.domain.ProductHolding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductHoldingRepository extends JpaRepository<ProductHolding, Integer> {
}

