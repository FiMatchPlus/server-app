package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.domain.HoldingSnapshot;
import com.stockone19.backend.backtest.domain.PortfolioSnapshot;
import com.stockone19.backend.backtest.dto.*;
import com.stockone19.backend.backtest.dto.BacktestStatus;
import com.stockone19.backend.backtest.domain.ResultStatus;
import com.stockone19.backend.backtest.domain.ExecutionLog;
import com.stockone19.backend.backtest.domain.ActionType;
import com.stockone19.backend.backtest.event.BacktestFailureEvent;
import com.stockone19.backend.backtest.event.BacktestSuccessEvent;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import com.stockone19.backend.backtest.repository.SnapshotRepository;
import com.stockone19.backend.backtest.repository.ExecutionLogJdbcRepository;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.common.service.BacktestJobMappingService;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestExecutionService {

    private final BacktestRepository backtestRepository;
    private final BacktestJobMappingService jobMappingService;
    private final PortfolioRepository portfolioRepository;
    private final SnapshotRepository snapshotRepository;
    private final ExecutionLogJdbcRepository executionLogJdbcRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    
    @Qualifier("backtestEngineWebClient")
    private final WebClient backtestEngineWebClient;
    
    @Value("${backtest.callback.base-url}")
    private String callbackBaseUrl;

    /**
     * 백테스트 실행 시작
     *
     * @param backtestId 백테스트 ID
     * @return 비동기 실행 결과
     */
    @Transactional
    public CompletableFuture<Void> startBacktest(Long backtestId) {
        log.info("Starting backtest execution for backtestId: {}", backtestId);
        
        // 백테스트 상태를 RUNNING으로 업데이트
        updateBacktestStatus(backtestId, BacktestStatus.RUNNING);
        
        // 백테스트 엔진에 비동기 요청 제출
        return submitToBacktestEngineAsync(backtestId);
    }

    /**
     * 백테스트 엔진에 비동기 요청 제출
     */
    @Async("backgroundTaskExecutor")
    public CompletableFuture<Void> submitToBacktestEngineAsync(Long backtestId) {
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("백테스트를 찾을 수 없습니다: " + backtestId));
            
            // 백테스트 요청 생성
            BacktestAsyncRequest request = createBacktestEngineRequest(backtest);
            
            // 백테스트 엔진에 요청
            BacktestStartResponse response = backtestEngineWebClient
                .post()
                .uri("/backtest/start")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(BacktestStartResponse.class)
                .block();
            
            // Redis에 매핑 저장
            jobMappingService.saveMapping(response.jobId(), backtestId);
            
            log.info("Backtest submitted to engine: backtestId={}, jobId={}", 
                    backtestId, response.jobId());
                    
        } catch (Exception e) {
            log.error("Failed to submit backtest to engine: backtestId={}", backtestId, e);
            // 이벤트 발행으로 상태 업데이트 처리
            eventPublisher.publishEvent(new BacktestFailureEvent(backtestId, e.getMessage()));
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 백테스트 성공 콜백 처리
     */
    @Async("backgroundTaskExecutor")
    public void handleBacktestSuccessCallback(BacktestCallbackResponse callback) {
        Long backtestId = jobMappingService.getAndRemoveMapping(callback.jobId());
        
        if (backtestId == null) {
            log.warn("Received callback for unknown jobId: {}", callback.jobId());
            return;
        }
        
        log.info("Publishing backtest success event for backtestId: {}, jobId: {}", backtestId, callback.jobId());
        // 이벤트 발행으로 성공 처리
        eventPublisher.publishEvent(new BacktestSuccessEvent(backtestId, callback));
    }

    /**
     * 백테스트 실패 콜백 처리
     */
    @Async("backgroundTaskExecutor")
    public void handleBacktestFailure(BacktestCallbackResponse callback) {
        Long backtestId = jobMappingService.getAndRemoveMapping(callback.jobId());
        
        if (backtestId == null) {
            log.warn("Received callback for unknown jobId: {}", callback.jobId());
            return;
        }
        
        setBacktestStatusToFailed(backtestId);
        log.info("Updated backtest status to FAILED: backtestId={}, jobId={}, error={}", 
                backtestId, callback.jobId(), callback.errorMessage());
    }

    /**
     * 백테스트 성공 이벤트 처리 - 개선된 2단계 커밋 방식
     */
    @EventListener
    @Transactional(propagation = REQUIRES_NEW)
    public void handleBacktestSuccessEvent(BacktestSuccessEvent event) {
        log.info("Handling backtest success event for backtestId: {}", event.backtestId());
        
        try {
            // 백테스트 성공 처리
            handleBacktestSuccess(event.backtestId(), event.callback());
            
            log.info("Backtest completed successfully: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId());
                    
        } catch (Exception e) {
            log.error("Failed to process backtest success event: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId(), e);
            
            // 성공 이벤트 처리 중 실패 시 백테스트 상태를 FAILED로 변경
            setBacktestStatusToFailed(event.backtestId());
            log.info("Updated backtest status to FAILED due to processing error: backtestId={}", event.backtestId());
        }
    }

    /**
     * 백테스트 성공 처리 - 2단계 커밋 방식
     */
    public void handleBacktestSuccess(Long backtestId, BacktestCallbackResponse callback) {
        Long portfolioSnapshotId = null;
        
        try {
            // 1단계: JPA 데이터 저장 (핵심 데이터)
            portfolioSnapshotId = saveJpaDataInTransaction(backtestId, callback);
            
            // 2단계: JDBC 배치 저장 (대량 데이터)
            saveJdbcDataInTransaction(portfolioSnapshotId, callback);
            
            log.info("Successfully processed backtest completion for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to process backtest success for backtestId: {}", backtestId, e);
            
            // JDBC 실패 시 JPA 데이터 롤백
            if (portfolioSnapshotId != null) {
                try {
                    rollbackJpaData(backtestId, portfolioSnapshotId);
                } catch (Exception rollbackException) {
                    log.error("Failed to rollback JPA data for backtestId: {}", backtestId, rollbackException);
                }
            }
            
            throw new RuntimeException("Failed to process backtest completion", e);
        }
    }

    /**
     * 백테스트 실패 이벤트 처리
     */
    @EventListener
    @Transactional(propagation = REQUIRES_NEW)
    public void handleBacktestFailure(BacktestFailureEvent event) {
        log.info("Handling backtest failure event for backtestId: {}, errorMessage: {}", 
                event.backtestId(), event.errorMessage());
        
        try {
            updateBacktestStatus(event.backtestId(), BacktestStatus.FAILED);
            log.info("Successfully updated backtest status to FAILED for backtestId: {}", event.backtestId());
        } catch (Exception e) {
            log.error("Failed to update backtest status to FAILED for backtestId: {}", event.backtestId(), e);
        }
    }

    /**
     * 백테스트 상태를 FAILED로 변경하는 공통 함수
     */
    private void setBacktestStatusToFailed(Long backtestId) {
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest not found: " + backtestId));
            
            backtest.updateStatus(BacktestStatus.FAILED);
            backtestRepository.save(backtest);
            
            log.info("Successfully updated backtest status to FAILED for backtestId: {}", backtestId);
        } catch (Exception e) {
            log.error("Failed to update backtest status to FAILED for backtestId: {}", backtestId, e);
        }
    }

    /**
     * 백테스트 상태 업데이트 (동기)
     */
    @Transactional(propagation = REQUIRES_NEW)
    public void updateBacktestStatus(Long backtestId, BacktestStatus status) {
        log.info("Updating backtest status to {} for backtestId: {}", status, backtestId);
        
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest not found with id: " + backtestId));
            
            backtest.updateStatus(status);
            backtestRepository.save(backtest);
            
            log.info("Successfully updated backtest status to {} for backtestId: {}", status, backtestId);
        } catch (Exception e) {
            log.error("Failed to update backtest status to {} for backtestId: {}", status, backtestId, e);
            throw new RuntimeException("Failed to update backtest status", e);
        }
    }

    /**
     * 백테스트 엔진 요청 생성
     */
    private BacktestAsyncRequest createBacktestEngineRequest(Backtest backtest) {
        // 포트폴리오에서 보유 종목 조회
        List<Holding> holdings = getHoldingsFromPortfolio(backtest.getPortfolioId());
        
        // 콜백 URL 생성 (백테스트 엔진이 완료 시 호출할 URL)
        String callbackUrl = callbackBaseUrl + "/backtests/callback";
        
        return BacktestAsyncRequest.of(
            backtest.getStartAt(),
            backtest.getEndAt(),
            holdings,
            callbackUrl
        );
    }

    /**
     * 포트폴리오에서 보유 종목 조회
     */
    private List<Holding> getHoldingsFromPortfolio(Long portfolioId) {
        return portfolioRepository.findHoldingsByPortfolioId(portfolioId);
    }

    /**
     * 상세 백테스트 결과 저장
     * 순서: PostgreSQL 작업 → MongoDB 작업 (비동기)
     */
    public void saveDetailedBacktestResults(Long backtestId, BacktestExecutionResponse response) {
        try {
            log.info("Saving detailed backtest results for backtestId: {}", backtestId);
            
            // 이미 변환된 BacktestExecutionResponse 사용
            if (response == null) {
                throw new IllegalStateException("BacktestExecutionResponse is null");
            }
            
            // PostgreSQL 작업 (메트릭 포함)을 트랜잭션에서 처리
            Long portfolioSnapshotId = savePostgresDataInTransaction(backtestId, response, null);
            log.info("Saved all backtest results (including metrics JSON) with portfolioSnapshotId: {}", portfolioSnapshotId);
            
            log.info("All detailed backtest results saved successfully for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to save backtest results for backtestId: {} - {}", backtestId, e.getMessage(), e);
            throw new RuntimeException("Failed to save backtest results for backtestId: " + backtestId, e);
        }
    }

    /**
     * PostgreSQL 데이터를 별도 트랜잭션에서 저장
     */
    @Transactional(timeout = 300)
    public Long savePostgresDataInTransaction(Long backtestId, BacktestExecutionResponse response, String metricId) {
        // 한 번만 조회한 backtest 객체 사용
        Backtest backtest = backtestRepository.findById(backtestId)
            .orElseThrow(() -> new ResourceNotFoundException("백테스트를 찾을 수 없습니다: " + backtestId));
        
        // 1단계: PostgreSQL에 포트폴리오 스냅샷 저장 (메트릭 JSON 포함)
        Long portfolioSnapshotId = savePortfolioSnapshot(backtest, response.portfolioSnapshot(), response.metrics());
        log.info("Step 1: Saved portfolio snapshot with metrics JSON, ID: {}", portfolioSnapshotId);
        
        // 2단계: PostgreSQL에 일별 홀딩 스냅샷 저장
        try {
            log.info("Starting to save daily holding snapshots for portfolioSnapshotId: {}", portfolioSnapshotId);
            saveDailyHoldingSnapshots(response.resultSummary(), portfolioSnapshotId);
            log.info("Step 2: Saved {} daily holding snapshots", getTotalHoldingSnapshotCount(response.resultSummary()));
        } catch (Exception e) {
            log.error("Failed to save daily holding snapshots for portfolioSnapshotId: {}", portfolioSnapshotId, e);
            throw e;
        }
        
        return portfolioSnapshotId;
    }

    /**
     * 저장될 홀딩 스냅샷 개수 계산 (로깅용)
     */
    private int getTotalHoldingSnapshotCount(List<BacktestExecutionResponse.DailyResultResponse> dailyResults) {
        return dailyResults.stream()
                .mapToInt(daily -> daily.stocks().size())
                .sum();
    }


    /**
     * PostgreSQL에 포트폴리오 스냅샷 저장 (메트릭 JSON 포함)
     * backtest 객체를 직접 전달받아 DB 조회 최적화
     */
    private Long savePortfolioSnapshot(Backtest backtest,
                                     BacktestExecutionResponse.PortfolioSnapshotResponse snapshotResponse,
                                     BacktestExecutionResponse.BacktestMetricsResponse metricsResponse) {
        
        // 메트릭을 JSON 문자열로 변환
        String metricsJson = convertMetricsToJson(metricsResponse);
        
        PortfolioSnapshot portfolioSnapshot = PortfolioSnapshot.create(
                backtest.getId(),        // portfolio_id 대신 backtest_id 사용
                snapshotResponse.baseValue(),
                snapshotResponse.currentValue(),
                metricsJson, // 메트릭을 JSON 문자열로 저장
                snapshotResponse.startAt() != null ? snapshotResponse.startAt() : backtest.getStartAt(),
                snapshotResponse.endAt() != null ? snapshotResponse.endAt() : backtest.getEndAt(),
                snapshotResponse.executionTime()
        );
        
        PortfolioSnapshot savedSnapshot = snapshotRepository.savePortfolioSnapshot(portfolioSnapshot);
        
        log.debug("Created portfolio snapshot with metrics JSON: portfolioId={}, baseValue={:.2f}, currentValue={:.2f}", 
                 backtest.getPortfolioId(), snapshotResponse.baseValue(), snapshotResponse.currentValue());
        
        return savedSnapshot.id();
    }

    /**
     * PostgreSQL에 포트폴리오 스냅샷 저장 (벤치마크 메트릭스 포함)
     * 콜백 응답에서 벤치마크 메트릭스를 함께 저장
     */
    private Long savePortfolioSnapshotWithBenchmark(Backtest backtest,
                                                   BacktestExecutionResponse.PortfolioSnapshotResponse snapshotResponse,
                                                   BacktestExecutionResponse.BacktestMetricsResponse metricsResponse,
                                                   BacktestCallbackResponse.BenchmarkMetricsResponse benchmarkMetrics) {
        
        // 메트릭을 JSON 문자열로 변환 (벤치마크 메트릭스 포함)
        String metricsJson = convertMetricsToJsonWithBenchmark(metricsResponse, benchmarkMetrics);
        
        PortfolioSnapshot portfolioSnapshot = PortfolioSnapshot.create(
                backtest.getId(),
                snapshotResponse.baseValue(),
                snapshotResponse.currentValue(),
                metricsJson,
                snapshotResponse.startAt() != null ? snapshotResponse.startAt() : backtest.getStartAt(),
                snapshotResponse.endAt() != null ? snapshotResponse.endAt() : backtest.getEndAt(),
                snapshotResponse.executionTime()
        );
        
        PortfolioSnapshot savedSnapshot = snapshotRepository.savePortfolioSnapshot(portfolioSnapshot);
        
        log.debug("Created portfolio snapshot with benchmark metrics JSON: portfolioId={}, baseValue={:.2f}, currentValue={:.2f}", 
                 backtest.getPortfolioId(), snapshotResponse.baseValue(), snapshotResponse.currentValue());
        
        return savedSnapshot.id();
    }

    /**
     * 메트릭 응답을 JSON 문자열로 변환
     */
    private String convertMetricsToJson(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse) {
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
            
            return objectMapper.writeValueAsString(metricsMap);
        } catch (Exception e) {
            log.error("Failed to convert metrics to JSON", e);
            throw new RuntimeException("Failed to convert metrics to JSON", e);
        }
    }

    /**
     * 메트릭과 벤치마크 메트릭스를 포함한 JSON 문자열로 변환
     */
    private String convertMetricsToJsonWithBenchmark(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse,
                                                    BacktestCallbackResponse.BenchmarkMetricsResponse benchmarkMetrics) {
        try {
            Map<String, Object> metricsMap = new HashMap<>();
            
            // 포트폴리오 메트릭
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
            
            // 벤치마크 메트릭 추가
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
     * PostgreSQL에 일별 홀딩 스냅샷 저장 (배치 INSERT로 성능 최적화)
     */
    private void saveDailyHoldingSnapshots(List<BacktestExecutionResponse.DailyResultResponse> dailyResults, 
                                         Long portfolioSnapshotId) {
        
        if (portfolioSnapshotId == null) {
            throw new IllegalArgumentException("portfolioSnapshotId cannot be null for holding snapshots");
        }
        
        // 모든 holdingSnapshot을 리스트로 수집
        List<HoldingSnapshot> holdingSnapshots = new ArrayList<>();
        
        for (BacktestExecutionResponse.DailyResultResponse dailyResult : dailyResults) {
            LocalDateTime date = dailyResult.date();
            
            for (BacktestExecutionResponse.DailyStockResponse stockData : dailyResult.stocks()) {
                try {
                    // quantity는 이제 직접 제공됨 (정확한 수량!)
                    int quantity = stockData.quantity();
                    
                    // value는 quantity × closePrice로 정확히 계산
                    double value = stockData.getValue(); // quantity * closePrice (더 정확함)
                    
                    // HoldingSnapshot 생성 (기존 레코드 그대로 사용)
                    HoldingSnapshot holdingSnapshot = HoldingSnapshot.createWithDate(
                            stockData.closePrice(),        // price
                            quantity,                       // quantity (직접 제공됨!)
                            value,                         // value (계산됨)
                            stockData.portfolioWeight(),   // weight
                            portfolioSnapshotId,           // portfolio_snapshot_id (NOT NULL)
                            stockData.stockCode(),         // stock_code
                            date,                          // recorded_at
                            stockData.portfolioContribution(), // contribution
                            stockData.dailyReturn()        // daily_ratio
                    );
                    
                    holdingSnapshots.add(holdingSnapshot);
                    
                } catch (Exception e) {
                    log.error("Failed to create holding snapshot: stockCode={}, date={}, portfolioSnapshotId={}", 
                             stockData.stockCode(), date, portfolioSnapshotId, e);
                    // 개별 실패는 로그만 남기고 계속 진행
                }
            }
        }
        
        // 배치 INSERT - 한 번에 처리!
        try {
            int savedCount = snapshotRepository.saveHoldingSnapshotsBatch(holdingSnapshots);
            log.info("Saved {} holding snapshots in batch for portfolioSnapshotId: {}", savedCount, portfolioSnapshotId);
        } catch (Exception e) {
            log.error("Failed to save holding snapshots in batch for portfolioSnapshotId: {}", portfolioSnapshotId, e);
            throw e;
        }
    }

    /**
     * JPA 데이터 저장 (핵심 데이터)
     */
    @Transactional(timeout = 60)
    public Long saveJpaDataInTransaction(Long backtestId, BacktestCallbackResponse callback) {
        // 1. Backtest 상태를 PENDING으로 설정 (JDBC 처리 전 상태)
        Backtest backtest = backtestRepository.findById(backtestId)
            .orElseThrow(() -> new ResourceNotFoundException("Backtest not found: " + backtestId));
        
        backtest.updateResultStatus(ResultStatus.PENDING); // 완료 전 상태
        backtestRepository.save(backtest);
        
        // 2. PortfolioSnapshot 저장 (벤치마크 메트릭스 포함)   
        BacktestExecutionResponse executionResponse = callback.toBacktestExecutionResponse();
        if (executionResponse != null) {
            Long portfolioSnapshotId;
            
            // 벤치마크 메트릭이 있는 경우 포함하여 저장
            if (callback.benchmarkMetrics() != null) {
                portfolioSnapshotId = savePortfolioSnapshotWithBenchmark(
                    backtest, 
                    executionResponse.portfolioSnapshot(), 
                    executionResponse.metrics(),
                    callback.benchmarkMetrics()
                );
            } else {
                portfolioSnapshotId = savePortfolioSnapshot(backtest, executionResponse.portfolioSnapshot(), executionResponse.metrics());
            }
            
            // 3. 벤치마크 정보는 로그로만 저장 (backtests 테이블은 수정하지 않음)
            if (callback.benchmarkInfo() != null) {
                logBenchmarkInfo(callback.benchmarkInfo());
            }
            
            // 4. 무위험 수익률 정보 로깅
            if (callback.riskFreeRateInfo() != null) {
                logRiskFreeRateInfo(callback.riskFreeRateInfo());
            }
            
            return portfolioSnapshotId;
        }
        
        throw new RuntimeException("Failed to convert callback to BacktestExecutionResponse");
    }
    
    /**
     * JDBC 배치 데이터 저장 (대량 데이터)
     */
    @Transactional(timeout = 300)
    public void saveJdbcDataInTransaction(Long portfolioSnapshotId, BacktestCallbackResponse callback) {
        try {
            log.info("Starting JDBC batch save for portfolioSnapshotId: {}", portfolioSnapshotId);
            
            BacktestExecutionResponse executionResponse = callback.toBacktestExecutionResponse();
            
            // 1. HoldingSnapshot 배치 저장
            if (executionResponse != null && executionResponse.resultSummary() != null && !executionResponse.resultSummary().isEmpty()) {
                log.info("Saving holding snapshots - {} daily results found", executionResponse.resultSummary().size());
                List<HoldingSnapshot> holdingSnapshots = createHoldingSnapshots(executionResponse.resultSummary(), portfolioSnapshotId);
                log.info("Created {} holding snapshots, starting batch save", holdingSnapshots.size());
                snapshotRepository.saveHoldingSnapshotsBatch(holdingSnapshots);
                log.info("Successfully saved holding snapshots");
            } else {
                log.info("No holding snapshots to save");
            }
            
            // 2. ExecutionLog 배치 저장
            if (callback.executionLogs() != null && !callback.executionLogs().isEmpty()) {
                log.info("Saving execution logs - {} logs found", callback.executionLogs().size());
                // PortfolioSnapshot에서 backtestId 조회
                Long backtestId = getBacktestIdFromPortfolioSnapshot(portfolioSnapshotId);
                List<ExecutionLog> executionLogs = createExecutionLogs(backtestId, callback.executionLogs());
                log.info("Created {} execution logs, starting batch insert", executionLogs.size());
                executionLogJdbcRepository.batchInsert(executionLogs);
                log.info("Successfully saved execution logs");
            } else {
                log.info("No execution logs to save");
            }
            
            // JDBC 성공 시 Backtest 상태를 결과 상태로 업데이트
            log.info("Updating backtest result status to completed");
            updateBacktestResultStatusToCompleted(portfolioSnapshotId, callback.resultStatus());
            log.info("JDBC batch save completed successfully for portfolioSnapshotId: {}", portfolioSnapshotId);
            
        } catch (Exception e) {
            log.error("JDBC batch save failed for portfolioSnapshotId: {}", portfolioSnapshotId, e);
            throw e; // 상위에서 롤백 처리
        }
    }
    
    /**
     * JPA 데이터 롤백 (JDBC 실패 시)
     */
    @Transactional
    public void rollbackJpaData(Long backtestId, Long portfolioSnapshotId) {
        log.info("Rolling back JPA data for backtestId: {}, portfolioSnapshotId: {}", backtestId, portfolioSnapshotId);
        
        try {
            // 1. PortfolioSnapshot 삭제
            snapshotRepository.deletePortfolioSnapshotById(portfolioSnapshotId);
            
            // 2. Backtest 상태를 FAILED로 변경
            Backtest backtest = backtestRepository.findById(backtestId).orElse(null);
            if (backtest != null) {
                backtest.updateResultStatus(ResultStatus.FAILED);
                backtestRepository.save(backtest);
            }
            
            log.info("Successfully rolled back JPA data for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to rollback JPA data for backtestId: {}", backtestId, e);
            // 롤백 실패는 별도 알림 처리 필요
        }
    }
    
    /**
     * ExecutionLog 엔티티 생성
     */
    private List<ExecutionLog> createExecutionLogs(Long backtestId, List<BacktestCallbackResponse.ExecutionLogResponse> logResponses) {
        return logResponses.stream()
            .map(logResponse -> ExecutionLog.builder()
                .backtestId(backtestId)
                .logDate(logResponse.date())
                .actionType(ActionType.valueOf(logResponse.action()))
                .category(logResponse.category())
                .triggerValue(logResponse.triggerValue())
                .thresholdValue(logResponse.thresholdValue())
                .reason(logResponse.reason())
                .portfolioValue(logResponse.portfolioValue())
                .build())
            .toList();
    }
    
    /**
     * 벤치마크 정보 로깅
     */
    private void logBenchmarkInfo(BacktestCallbackResponse.BenchmarkInfoResponse benchmarkInfoResponse) {
        log.info("Benchmark Info - Code: {}, Latest Price: {}, Latest Date: {}, Change Rate: {}, Data Range: {} ~ {}", 
                benchmarkInfoResponse.benchmarkCode(),
                benchmarkInfoResponse.latestPrice(),
                benchmarkInfoResponse.latestDate(),
                benchmarkInfoResponse.latestChangeRate(),
                benchmarkInfoResponse.dataRange() != null ? benchmarkInfoResponse.dataRange().startDate() : "N/A",
                benchmarkInfoResponse.dataRange() != null ? benchmarkInfoResponse.dataRange().endDate() : "N/A");
    }
    
    /**
     * 무위험 수익률 정보 로깅
     */
    private void logRiskFreeRateInfo(BacktestCallbackResponse.RiskFreeRateInfoResponse riskFreeRateInfoResponse) {
        log.info("Risk-Free Rate Info - Rate Type: {}, Avg Annual Rate: {}%, Data Points: {}, Backtest Days: {}, Period Classification: {}, Selection Reason: {}", 
                riskFreeRateInfoResponse.rateType(),
                riskFreeRateInfoResponse.avgAnnualRate(),
                riskFreeRateInfoResponse.dataPoints(),
                riskFreeRateInfoResponse.decisionInfo() != null ? riskFreeRateInfoResponse.decisionInfo().backtestDays() : "N/A",
                riskFreeRateInfoResponse.decisionInfo() != null ? riskFreeRateInfoResponse.decisionInfo().periodClassification() : "N/A",
                riskFreeRateInfoResponse.decisionInfo() != null ? riskFreeRateInfoResponse.decisionInfo().selectionReason() : "N/A");
        
        if (riskFreeRateInfoResponse.rateInfo() != null) {
            log.info("Rate Details - Latest Rate: {}%, Source: {}, Rate Type: {}", 
                    riskFreeRateInfoResponse.rateInfo().latestRate(),
                    riskFreeRateInfoResponse.rateInfo().source(),
                    riskFreeRateInfoResponse.rateInfo().rateType());
        }
    }
    
    /**
     * PortfolioSnapshot에서 backtestId 조회
     */
    private Long getBacktestIdFromPortfolioSnapshot(Long portfolioSnapshotId) {
        PortfolioSnapshot snapshot = snapshotRepository.findById(portfolioSnapshotId);
        if (snapshot != null) {
            return snapshot.backtestId();
        }
        throw new ResourceNotFoundException("PortfolioSnapshot not found with id: " + portfolioSnapshotId);
    }
    
    /**
     * 백테스트 결과 상태 업데이트 
     */
    private void updateBacktestResultStatusToCompleted(Long portfolioSnapshotId, String resultStatus) {
        try {
            // PortfolioSnapshot에서 backtest_id 조회 후 상태 업데이트
            Long backtestId = getBacktestIdFromPortfolioSnapshot(portfolioSnapshotId);
            Backtest backtest = backtestRepository.findById(backtestId).orElse(null);
            if (backtest != null && resultStatus != null) {
                // 백테스트 상태를 COMPLETED로 변경 (RUNNING → COMPLETED)
                backtest.updateStatus(BacktestStatus.COMPLETED);
                
                // 결과 상태도 업데이트 (PENDING → COMPLETED)
                backtest.updateResultStatus(ResultStatus.valueOf(resultStatus));
                
                backtestRepository.save(backtest);
                
                log.info("Updated backtest status to COMPLETED and result status to {} for backtestId: {}", 
                         resultStatus, backtestId);
            }
        } catch (Exception e) {
            log.error("Failed to update backtest result status for portfolioSnapshotId: {}", portfolioSnapshotId, e);
        }
    }
    
    /**
     * HoldingSnapshot 리스트 생성 (DB 저장용 객체 생성)
     */
    private List<HoldingSnapshot> createHoldingSnapshots(List<BacktestExecutionResponse.DailyResultResponse> dailyResults, Long portfolioSnapshotId) {
        if (portfolioSnapshotId == null) {
            throw new IllegalArgumentException("portfolioSnapshotId cannot be null for holding snapshots");
        }
        
        List<HoldingSnapshot> holdingSnapshots = new ArrayList<>();
        
        for (BacktestExecutionResponse.DailyResultResponse dailyResult : dailyResults) {
            LocalDateTime date = dailyResult.date();
            
            for (BacktestExecutionResponse.DailyStockResponse stockData : dailyResult.stocks()) {
                try {
                    int quantity = stockData.quantity();
                    double value = stockData.getValue();
                    
                    HoldingSnapshot holdingSnapshot = HoldingSnapshot.createWithDate(
                        stockData.closePrice(),
                        quantity,
                        value,
                        stockData.portfolioWeight(),
                        portfolioSnapshotId,
                        stockData.stockCode(),
                        date,
                        stockData.portfolioContribution(),
                        stockData.dailyReturn()
                    );
                    
                    holdingSnapshots.add(holdingSnapshot);
                    
                } catch (Exception e) {
                    log.error("Failed to create holding snapshot: stockCode={}, date={}, portfolioSnapshotId={}", 
                             stockData.stockCode(), date, portfolioSnapshotId, e);
                }
            }
        }
        
        return holdingSnapshots;
    }
    
    
}
