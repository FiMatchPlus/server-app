package com.stockone19.backend.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "volatility_index", nullable = false, precision = 5, scale = 2)
    private BigDecimal volatilityIndex;

    @Column(name = "one_year_return", nullable = false, precision = 6, scale = 2)
    private BigDecimal oneYearReturn;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal mdd;

    @Column(name = "sharpe_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal sharpeRatio;

    @Column(name = "min_investment", nullable = false)
    private Long minInvestment;

    @Column(nullable = false, columnDefinition = "TEXT[]")
    private String[] keywords;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("displayOrder ASC")
    private List<ProductHolding> holdings = new ArrayList<>();

    public Product(String name, String description, RiskLevel riskLevel,
                   BigDecimal volatilityIndex, BigDecimal oneYearReturn,
                   BigDecimal mdd, BigDecimal sharpeRatio, Long minInvestment,
                   String[] keywords) {
        this.name = name;
        this.description = description;
        this.riskLevel = riskLevel;
        this.volatilityIndex = volatilityIndex;
        this.oneYearReturn = oneYearReturn;
        this.mdd = mdd;
        this.sharpeRatio = sharpeRatio;
        this.minInvestment = minInvestment;
        this.keywords = keywords;
    }
}

