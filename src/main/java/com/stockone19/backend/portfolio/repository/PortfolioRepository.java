package com.stockone19.backend.portfolio.repository;

import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.PortfolioSnapshot;
import com.stockone19.backend.portfolio.domain.HoldingSnapshot;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {

    List<Portfolio> findByUserId(Long userId);

    Optional<Portfolio> findById(Long portfolioId);

    Optional<Portfolio> findMainPortfolioByUserId(Long userId);

    Portfolio save(Portfolio portfolio);

    List<PortfolioSnapshot> findSnapshotsByPortfolioId(Long portfolioId);

    Optional<PortfolioSnapshot> findLatestSnapshotByPortfolioId(Long portfolioId);

    PortfolioSnapshot saveSnapshot(PortfolioSnapshot snapshot);

    List<HoldingSnapshot> findHoldingsBySnapshotId(Long snapshotId);

    HoldingSnapshot saveHolding(HoldingSnapshot holding);
}
