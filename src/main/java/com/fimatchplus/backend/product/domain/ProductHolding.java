package com.fimatchplus.backend.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "product_holdings")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProductHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false, length = 50)
    private String sector;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public ProductHolding(Product product, String symbol, String name,
                          BigDecimal weight, String sector, Integer displayOrder) {
        this.product = product;
        this.symbol = symbol;
        this.name = name;
        this.weight = weight;
        this.sector = sector;
        this.displayOrder = displayOrder;
    }
}

