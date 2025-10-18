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
import java.util.ArrayList;
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
                    .soldStocks(convertSoldStocksToJson(logResponse.soldStocks()))
                    .cashGenerated(logResponse.cashGenerated())
                    .createdAt(LocalDateTime.now()) // createdAt 값 명시적 설정
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
        List<HoldingSnapshot> snapshots = new ArrayList<>();
        
        for (BacktestExecutionResponse.DailyResultResponse daily : resultSummary) {
            // 1. 개별 주식 데이터 저장 (기존 로직)
            List<HoldingSnapshot> stockSnapshots = daily.stocks().stream()
                .map(stock -> HoldingSnapshot.createWithDate(
                    stock.closePrice(),
                    stock.quantity(),
                    stock.getValue(),
                    stock.portfolioWeight(),
                    portfolioSnapshotId,
                    stock.stockCode(),
                    daily.date(),
                    stock.portfolioContribution(),
                    stock.dailyReturn()
                ))
                .toList();
            snapshots.addAll(stockSnapshots);
            
            // 2. 일별 포트폴리오 레벨 데이터 저장 (새로운 로직)
            if (daily.portfolioValue() != null || daily.stockValue() != null || daily.cashBalance() != null) {
                HoldingSnapshot portfolioSnapshot = HoldingSnapshot.createWithDate(
                    0.0,                    // price = 0 (포트폴리오 레벨)
                    0,                      // quantity = 0 (포트폴리오 레벨)
                    daily.portfolioValue() != null ? daily.portfolioValue() : 0.0,  // value = portfolio_value
                    0.0,                    // weight = 0 (포트폴리오 레벨)
                    portfolioSnapshotId,
                    "PORTFOLIO_DAILY",      // stock_code = 특별한 식별자
                    daily.date(),
                    daily.stockValue() != null ? daily.stockValue() : 0.0,      // contribution = stock_value
                    daily.cashBalance() != null ? daily.cashBalance() : 0.0     // daily_ratio = cash_balance
                );
                snapshots.add(portfolioSnapshot);
                
                // quantities 정보가 있으면 로그로 기록 (나중에 필요시 별도 저장 방식 고려)
                if (daily.quantities() != null && !daily.quantities().isEmpty()) {
                    try {
                        String quantitiesJson = objectMapper.writeValueAsString(daily.quantities());
                        log.debug("Daily quantities for date {}: {}", daily.date(), quantitiesJson);
                    } catch (Exception e) {
                        log.warn("Failed to serialize quantities for date {}: {}", daily.date(), e.getMessage());
                    }
                }
            }
        }
        
        return snapshots;
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

    /**
     * soldStocks Map을 JSON 문자열로 변환
     */
    private String convertSoldStocksToJson(Map<String, Integer> soldStocks) {
        if (soldStocks == null || soldStocks.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(soldStocks);
        } catch (Exception e) {
            log.error("Failed to convert soldStocks to JSON", e);
            return null;
        }
    }
}
