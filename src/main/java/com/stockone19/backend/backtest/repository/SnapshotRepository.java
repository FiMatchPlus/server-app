package com.stockone19.backend.backtest.repository;

import com.stockone19.backend.backtest.domain.HoldingSnapshot;
import com.stockone19.backend.backtest.domain.PortfolioSnapshot;

import java.util.List;

/**
 * 백테스트 결과 스냅샷 저장 및 조회를 위한 리포지토리
 */
public interface SnapshotRepository {
    
    // PortfolioSnapshot 관련 메서드들
    PortfolioSnapshot savePortfolioSnapshot(PortfolioSnapshot snapshot);
    boolean existsPortfolioSnapshotByPortfolioId(Long portfolioId);
    List<PortfolioSnapshot> findPortfolioSnapshotsByPortfolioId(Long portfolioId);
    
    // HoldingSnapshot 관련 메서드들
    HoldingSnapshot saveHoldingSnapshot(HoldingSnapshot holdingSnapshot);
    List<HoldingSnapshot> findHoldingSnapshotsByPortfolioSnapshotId(Long portfolioSnapshotId);
}
