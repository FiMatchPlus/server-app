package com.fimatchplus.backend.product.repository;

import com.fimatchplus.backend.product.domain.ProductHolding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductHoldingRepository extends JpaRepository<ProductHolding, Integer> {
}

