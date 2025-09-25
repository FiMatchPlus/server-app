package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.BacktestMetricsDocument;
import com.stockone19.backend.backtest.dto.BacktestExecutionResponse;
import com.stockone19.backend.backtest.repository.BacktestMetricsRepository;
import com.stockone19.backend.backtest.repository.SnapshotRepository;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MongoDB 백테스트 메트릭 전용 서비스 (트랜잭션 완전 분리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MongoBacktestMetricsService {

    private final BacktestMetricsRepository backtestMetricsRepository;
    private final SnapshotRepository snapshotRepository;
    private final BacktestRepository backtestRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * MongoDB에 성과 지표를 동기적으로 저장 (트랜잭션 밖에서)
     */
    public void saveMetricsSync(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse, 
                               Long portfolioSnapshotId) {
        try {
            log.info("Starting MongoDB metrics save for portfolioSnapshotId: {}", portfolioSnapshotId);
            
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
            
            BacktestMetricsDocument savedMetrics = backtestMetricsRepository.save(metricsDoc);
            log.info("Successfully saved MongoDB metrics: metricId={}, portfolioSnapshotId={}", 
                    savedMetrics.getId(), portfolioSnapshotId);
                    
        } catch (Exception e) {
            log.error("Failed to save MongoDB metrics synchronously: portfolioSnapshotId={}, error={}", 
                     portfolioSnapshotId, e.getMessage(), e);
            throw e; // 예외를 다시 던져서 호출자에게 알림
        }
    }

    /**
     * MongoDB에 성과 지표를 완전히 비동기로 저장 (트랜잭션 밖에서)
     */
    @Async("backgroundTaskExecutor")
    public void saveMetricsAsync(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse, 
                               Long portfolioSnapshotId) {
        try {
            log.info("Starting MongoDB metrics save for portfolioSnapshotId: {}", portfolioSnapshotId);
            
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
            
            BacktestMetricsDocument savedMetrics = backtestMetricsRepository.save(metricsDoc);
            log.info("Successfully saved MongoDB metrics: metricId={}, portfolioSnapshotId={}", 
                    savedMetrics.getId(), portfolioSnapshotId);
                    
        } catch (Exception e) {
            log.error("Failed to save MongoDB metrics asynchronously: portfolioSnapshotId={}, error={}", 
                     portfolioSnapshotId, e.getMessage(), e);
        }
    }

    /**
     * MongoDB 실패 시 백테스트 상태만 FAILED로 변경 (비동기)
     * PostgreSQL 데이터는 그대로 유지 (데이터 손실 방지)
     */
    @Async("backgroundTaskExecutor")
    public void markBacktestAsFailedAsync(Long backtestId) {
        try {
            log.info("Marking backtest as FAILED for backtestId: {}", backtestId);
            
            // 백테스트 상태만 FAILED로 변경 (트랜잭션 최소화)
            updateBacktestStatusToFailed(backtestId);
            log.info("Updated backtest status to FAILED for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to mark backtest as FAILED for backtestId: {}, error={}", 
                     backtestId, e.getMessage(), e);
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
