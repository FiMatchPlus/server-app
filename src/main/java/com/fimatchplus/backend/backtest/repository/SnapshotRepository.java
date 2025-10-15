package com.fimatchplus.backend.backtest.repository;

import com.fimatchplus.backend.backtest.domain.HoldingSnapshot;
import com.fimatchplus.backend.backtest.domain.PortfolioSnapshot;

import java.util.List;

/**
 * 백테스트 결과 스냅샷 저장 및 조회를 위한 리포지토리
 */
public interface SnapshotRepository {
    
    PortfolioSnapshot savePortfolioSnapshot(PortfolioSnapshot snapshot);
    PortfolioSnapshot findLatestPortfolioSnapshotByBacktestId(Long backtestId);
    PortfolioSnapshot findById(Long id);
    
    HoldingSnapshot saveHoldingSnapshot(HoldingSnapshot holdingSnapshot);
    int saveHoldingSnapshotsBatch(List<HoldingSnapshot> holdingSnapshots);
    List<HoldingSnapshot> findHoldingSnapshotsByBacktestId(Long backtestId);

    int deletePortfolioSnapshotById(Long portfolioSnapshotId);
}
