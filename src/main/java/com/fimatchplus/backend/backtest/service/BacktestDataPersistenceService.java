package com.fimatchplus.backend.backtest.service;

import com.fimatchplus.backend.backtest.domain.HoldingSnapshot;
import com.fimatchplus.backend.backtest.domain.PortfolioSnapshot;
import com.fimatchplus.backend.backtest.domain.ExecutionLog;
import com.fimatchplus.backend.backtest.domain.ActionType;
import com.fimatchplus.backend.backtest.dto.BacktestCallbackResponse;
import com.fimatchplus.backend.backtest.dto.BacktestExecutionResponse;
import com.fimatchplus.backend.backtest.repository.SnapshotRepository;
import com.fimatchplus.backend.backtest.repository.ExecutionLogJdbcRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 백테스트 데이터 영속성 관리 서비스
 * JPA와 JDBC 데이터 저장을 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestDataPersistenceService {

    private final SnapshotRepository snapshotRepository;
    private final ExecutionLogJdbcRepository executionLogJdbcRepository;
    private final ObjectMapper objectMapper;

    /**
     * JPA 데이터 저장 (트랜잭션 내)
     */
    @Transactional
    public Long saveJpaDataInTransaction(Long backtestId, BacktestCallbackResponse callback) {
        log.info("Saving JPA data for backtestId: {}", backtestId);
        
        try {
            PortfolioSnapshot portfolioSnapshot = createPortfolioSnapshot(backtestId, callback);
            PortfolioSnapshot savedSnapshot = snapshotRepository.savePortfolioSnapshot(portfolioSnapshot);
            
            log.info("Successfully saved PortfolioSnapshot with ID: {}", savedSnapshot.id());
            return savedSnapshot.id();
            
        } catch (Exception e) {
            log.error("Failed to save JPA data for backtestId: {}", backtestId, e);
            throw new RuntimeException("Failed to save JPA data", e);
        }
    }

    /**
     * JDBC 배치 저장 (트랜잭션 내)
     */
    @Transactional
    public void saveJdbcDataInTransaction(Long portfolioSnapshotId, BacktestCallbackResponse callback) {
        log.info("Saving JDBC batch data for portfolioSnapshotId: {}", portfolioSnapshotId);
        
        try {
            if (callback.executionLogs() != null && !callback.executionLogs().isEmpty()) {
                List<ExecutionLog> executionLogs = createExecutionLogs(portfolioSnapshotId, callback.executionLogs());
                int savedLogs = executionLogJdbcRepository.optimizedBatchInsert(executionLogs);
                log.info("Successfully saved {} execution logs", savedLogs);
            }
            
            if (callback.resultSummary() != null && !callback.resultSummary().isEmpty()) {
                List<HoldingSnapshot> holdingSnapshots = createHoldingSnapshotsFromResultSummary(
                    portfolioSnapshotId,
                    callback.resultSummary()
                );
                snapshotRepository.saveHoldingSnapshotsBatch(holdingSnapshots);
                log.info("Successfully saved {} holding snapshots from result_summary", holdingSnapshots.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to save JDBC batch data for portfolioSnapshotId: {}", portfolioSnapshotId, e);
            throw new RuntimeException("Failed to save JDBC batch data", e);
        }
    }

    /**
     * JPA 데이터 롤백
     */
    @Transactional
    public void rollbackJpaData(Long backtestId, Long portfolioSnapshotId) {
        log.info("Rolling back JPA data for backtestId: {}, portfolioSnapshotId: {}", backtestId, portfolioSnapshotId);
        
        try {
            snapshotRepository.deletePortfolioSnapshotById(portfolioSnapshotId);
            
            log.info("Successfully rolled back JPA data for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to rollback JPA data for backtestId: {}", backtestId, e);
            throw new RuntimeException("Failed to rollback JPA data", e);
        }
    }

    /**
     * PortfolioSnapshot 생성
     */
    private PortfolioSnapshot createPortfolioSnapshot(Long backtestId, BacktestCallbackResponse callback) {
        BacktestCallbackResponse.PortfolioSnapshotResponse portfolioSnapshot = callback.portfolioSnapshot();
        
        return PortfolioSnapshot.create(
            backtestId,
            portfolioSnapshot.baseValue(),
            portfolioSnapshot.currentValue(),
            convertMetricsToJson(callback.metrics(), callback.benchmarkMetrics()),
            parseDateTime(portfolioSnapshot.startAt()),
            parseDateTime(portfolioSnapshot.endAt()),
            portfolioSnapshot.getExecutionTimeAsDouble()
        );
    }

    /**
     * 메트릭과 벤치마크 메트릭스를 포함한 JSON 문자열로 변환
     */
    private String convertMetricsToJson(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse,
                                       BacktestCallbackResponse.BenchmarkMetricsResponse benchmarkMetrics) {
        try {
            Map<String, Object> metricsMap = new HashMap<>();
            
            metricsMap.put("totalReturn", metricsResponse.totalReturn());
            metricsMap.put("annualizedReturn", metricsResponse.annualizedReturn());
            metricsMap.put("volatility", metricsResponse.volatility());
            metricsMap.put("sharpeRatio", metricsResponse.sharpeRatio());
            metricsMap.put("maxDrawdown", metricsResponse.maxDrawdown());
            metricsMap.put("var95", metricsResponse.var95());
            metricsMap.put("var99", metricsResponse.var99());
            metricsMap.put("cvar95", metricsResponse.cvar95());
            metricsMap.put("cvar99", metricsResponse.cvar99());
            metricsMap.put("winRate", metricsResponse.winRate());
            metricsMap.put("profitLossRatio", metricsResponse.profitLossRatio());
            
            if (benchmarkMetrics != null) {
                Map<String, Object> benchmarkMap = new HashMap<>();
                benchmarkMap.put("benchmark_total_return", benchmarkMetrics.benchmarkTotalReturn());
                benchmarkMap.put("benchmark_volatility", benchmarkMetrics.benchmarkVolatility());
                benchmarkMap.put("benchmark_max_price", benchmarkMetrics.benchmarkMaxPrice());
                benchmarkMap.put("benchmark_min_price", benchmarkMetrics.benchmarkMinPrice());
                benchmarkMap.put("alpha", benchmarkMetrics.alpha());
                benchmarkMap.put("benchmark_daily_average", benchmarkMetrics.benchmarkDailyAverage());
                
                metricsMap.put("benchmark", benchmarkMap);
            }
            
            return objectMapper.writeValueAsString(metricsMap);
        } catch (Exception e) {
            log.error("Failed to convert metrics to JSON", e);
            throw new RuntimeException("Failed to convert metrics to JSON", e);
        }
    }

    /**
     * 날짜 문자열을 LocalDateTime으로 변환
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr);
        } catch (Exception e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }

    /**
     * ExecutionLog 엔티티 생성
     */
    private List<ExecutionLog> createExecutionLogs(Long backtestId, List<BacktestCallbackResponse.ExecutionLogResponse> logResponses) {
        return logResponses.stream()
            .map(logResponse -> {
                LocalDateTime logDate = logResponse.date();
                if (logDate == null) {
                    logDate = LocalDateTime.now();
                    log.warn("ExecutionLog date is null for backtestId: {}, action: {}, using current time: {}", 
                        backtestId, logResponse.action(), logDate);
                }
                
                return ExecutionLog.builder()
                    .backtestId(backtestId)
                    .logDate(logDate)
                    .actionType(convertActionType(logResponse.action()))
                    .category(logResponse.category())
                    .triggerValue(logResponse.triggerValue())
                    .thresholdValue(logResponse.thresholdValue())
                    .reason(logResponse.reason())
                    .portfolioValue(logResponse.portfolioValue())
                    .build();
            })
            .toList();
    }

    /**
     * HoldingSnapshot 엔티티 생성
     */
    private List<HoldingSnapshot> createHoldingSnapshotsFromResultSummary(
            Long portfolioSnapshotId,
            List<BacktestExecutionResponse.DailyResultResponse> resultSummary
    ) {
        return resultSummary.stream()
            .flatMap(daily -> daily.stocks().stream().map(stock -> HoldingSnapshot.createWithDate(
                stock.closePrice(),
                stock.quantity(),
                stock.getValue(),
                stock.portfolioWeight(),
                portfolioSnapshotId,
                stock.stockCode(),
                daily.date(),
                stock.portfolioContribution(),
                stock.dailyReturn()
            )))
            .toList();
    }

    /**
     * ActionType 변환
     */
    private ActionType convertActionType(String action) {
        return switch (action.toLowerCase()) {
            case "stop_loss" -> ActionType.STOP_LOSS;
            case "take_profit" -> ActionType.TAKE_PROFIT;
            case "rebalance" -> ActionType.REBALANCE;
            case "buy" -> ActionType.BUY;
            case "sell" -> ActionType.SELL;
            case "liquidation" -> ActionType.LIQUIDATION;
            default -> throw new IllegalArgumentException("Unknown action type: " + action);
        };
    }
}
