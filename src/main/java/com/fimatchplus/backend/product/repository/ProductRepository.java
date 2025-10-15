package com.fimatchplus.backend.product.repository;

import com.fimatchplus.backend.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}

