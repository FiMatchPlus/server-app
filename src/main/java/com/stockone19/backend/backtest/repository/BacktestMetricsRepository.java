package com.stockone19.backend.backtest.repository;

import com.stockone19.backend.backtest.domain.BacktestMetricsDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 백테스트 성과 지표 MongoDB Repository
 */
@Repository
public interface BacktestMetricsRepository extends MongoRepository<BacktestMetricsDocument, String> {

    /**
     * 포트폴리오 스냅샷 ID로 성과 지표 조회
     */
    Optional<BacktestMetricsDocument> findByPortfolioSnapshotId(Long portfolioSnapshotId);

    /**
     * 여러 포트폴리오 스냅샷 ID로 성과 지표 목록 조회
     */
    @Query("{'portfolio_snapshot_id': {'$in': ?0}}")
    List<BacktestMetricsDocument> findByPortfolioSnapshotIdIn(List<Long> portfolioSnapshotIds);
}
