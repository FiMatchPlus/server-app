package com.stockone19.backend.portfolio.repository;

import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.portfolio.domain.PortfolioSnapshot;
import com.stockone19.backend.portfolio.domain.HoldingSnapshot;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {

    List<Portfolio> findByUserId(Long userId);

    Optional<Portfolio> findById(Long portfolioId);

    Optional<Portfolio> findMainPortfolioByUserId(Long userId);

    Portfolio save(Portfolio portfolio);

    Holding saveHolding(Holding holding);
    List<Holding> findHoldingsByPortfolioId(Long portfolioId);
    
    boolean existsSnapshotByPortfolioId(Long portfolioId);
    List<PortfolioSnapshot> findSnapshotsByPortfolioId(Long portfolioId);
    List<HoldingSnapshot> findHoldingSnapshotsByPortfolioSnapshotId(Long portfolioSnapshotId);
    
    // 백테스트 결과 저장용 메서드들
    PortfolioSnapshot saveSnapshot(PortfolioSnapshot snapshot);
    HoldingSnapshot saveHoldingSnapshot(HoldingSnapshot holdingSnapshot);
}
