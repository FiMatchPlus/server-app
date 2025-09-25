package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.BacktestMetricsDocument;
import com.stockone19.backend.backtest.dto.BacktestExecutionResponse;
import com.stockone19.backend.backtest.repository.BacktestMetricsRepository;
import com.stockone19.backend.backtest.repository.SnapshotRepository;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * MongoDB 백테스트 메트릭 전용 서비스 (트랜잭션 완전 분리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MongoBacktestMetricsService {

    private final BacktestMetricsRepository backtestMetricsRepository;
    private final SnapshotRepository snapshotRepository;
    private final MongoTemplate mongoTemplate;
    private final JdbcTemplate jdbcTemplate;

    /**
     * MongoDB에 성과 지표를 동기적으로 저장 (MongoTemplate 직접 사용으로 트랜잭션 완전 우회)
     */
    public void saveMetricsSync(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse, 
                               Long portfolioSnapshotId) {
        try {
            log.info("Starting MongoDB metrics save using MongoTemplate for portfolioSnapshotId: {}", portfolioSnapshotId);
            
            BacktestMetricsDocument metricsDoc = new BacktestMetricsDocument(
                    portfolioSnapshotId,
                    metricsResponse.totalReturn(),
                    metricsResponse.annualizedReturn(),
                    metricsResponse.volatility(),
                    metricsResponse.sharpeRatio(),
                    metricsResponse.maxDrawdown(),
                    metricsResponse.var95(),
                    metricsResponse.var99(),
                    metricsResponse.cvar95(),
                    metricsResponse.cvar99(),
                    metricsResponse.winRate(),
                    metricsResponse.profitLossRatio()
            );
            
            // MongoTemplate 직접 사용 - Spring Data MongoDB 자동 트랜잭션 관리 우회
            BacktestMetricsDocument savedMetrics = mongoTemplate.insert(metricsDoc, "metrics");
            log.info("Successfully saved MongoDB metrics using MongoTemplate: metricId={}, portfolioSnapshotId={}", 
                    savedMetrics.getId(), portfolioSnapshotId);
                    
        } catch (Exception e) {
            log.error("Failed to save MongoDB metrics using MongoTemplate: portfolioSnapshotId={}, error={}", 
                     portfolioSnapshotId, e.getMessage(), e);
            throw e; // 예외를 다시 던져서 호출자에게 알림
        }
    }

    /**
     * MongoDB 실패 시 PostgreSQL 데이터 롤백(비동기)
     * 순서: holding_snapshots 삭제 → portfolio_snapshots 삭제 → backtests 상태 FAILED로 변경
     */
    @Async("backgroundTaskExecutor")
    public void cleanupPostgresDataAsync(Long portfolioSnapshotId, Long backtestId) {
        try {
            log.info("Starting PostgreSQL cleanup for portfolioSnapshotId: {}, backtestId: {}", portfolioSnapshotId, backtestId);
            
            // 1단계: holding_snapshots 삭제 (JDBC - 트랜잭션 없음)
            int deletedHoldingSnapshots = snapshotRepository.deleteHoldingSnapshotsByPortfolioSnapshotId(portfolioSnapshotId);
            log.info("Deleted {} holding snapshots for portfolioSnapshotId: {}", deletedHoldingSnapshots, portfolioSnapshotId);
            
            // 2단계: portfolio_snapshots 삭제 (JDBC - 트랜잭션 없음)
            int deletedPortfolioSnapshots = snapshotRepository.deletePortfolioSnapshotById(portfolioSnapshotId);
            log.info("Deleted {} portfolio snapshots for portfolioSnapshotId: {}", deletedPortfolioSnapshots, portfolioSnapshotId);
            
            // 3단계: backtests 상태를 FAILED로 변경 (JDBC - 트랜잭션 없음)
            if (backtestId != null) {
                updateBacktestStatusToFailed(backtestId);
                log.info("Updated backtest status to FAILED for backtestId: {}", backtestId);
            }
            
            log.info("Successfully cleaned up PostgreSQL data for portfolioSnapshotId: {}, backtestId: {}", portfolioSnapshotId, backtestId);
            
        } catch (Exception e) {
            log.error("Failed to cleanup PostgreSQL data for portfolioSnapshotId: {}, backtestId: {}, error={}", 
                     portfolioSnapshotId, backtestId, e.getMessage(), e);
        }
    }

    /**
     * 백테스트 상태를 FAILED로 변경 (트랜잭션 없이 JDBC 직접 실행)
     */
    public void updateBacktestStatusToFailed(Long backtestId) {
        // JDBC Template을 사용해서 트랜잭션 없이 직접 실행
        String sql = "UPDATE backtests SET status = 'FAILED' WHERE id = ?";
        jdbcTemplate.update(sql, backtestId);
    }
}
