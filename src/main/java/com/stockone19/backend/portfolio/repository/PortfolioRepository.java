package com.stockone19.backend.portfolio.repository;

import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.Holding;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {

    List<Portfolio> findByUserId(Long userId);

    Optional<Portfolio> findById(Long portfolioId);

    Optional<Portfolio> findMainPortfolioByUserId(Long userId);

    Portfolio save(Portfolio portfolio);

    Holding saveHolding(Holding holding);
    List<Holding> findHoldingsByPortfolioId(Long portfolioId);
}
