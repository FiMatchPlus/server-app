package com.fimatchplus.backend.portfolio.repository;

import com.fimatchplus.backend.portfolio.domain.Portfolio;
import com.fimatchplus.backend.portfolio.domain.Holding;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {

    List<Portfolio> findByUserId(Long userId);

    Optional<Portfolio> findById(Long portfolioId);

    Portfolio save(Portfolio portfolio);

    void softDelete(Long portfolioId);

    Holding saveHolding(Holding holding);
    List<Holding> findHoldingsByPortfolioId(Long portfolioId);
    List<Holding> findHoldingsByUserId(Long userId);
    void deleteHoldingsByPortfolioId(Long portfolioId);
}
