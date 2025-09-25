package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.domain.BacktestMetricsDocument;
import com.stockone19.backend.backtest.domain.HoldingSnapshot;
import com.stockone19.backend.backtest.domain.PortfolioSnapshot;
import com.stockone19.backend.backtest.dto.*;
import com.stockone19.backend.backtest.event.BacktestFailureEvent;
import com.stockone19.backend.backtest.event.BacktestSuccessEvent;
import com.stockone19.backend.backtest.repository.BacktestMetricsRepository;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import com.stockone19.backend.backtest.repository.SnapshotRepository;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestExecutionService {

    private final BacktestRepository backtestRepository;
    private final BacktestJobMappingService jobMappingService;
    private final PortfolioRepository portfolioRepository;
    private final SnapshotRepository snapshotRepository;
    private final BacktestMetricsRepository backtestMetricsRepository;
    private final MongoBacktestMetricsService mongoBacktestMetricsService;
    private final ApplicationEventPublisher eventPublisher;
    
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
    public void handleBacktestSuccess(BacktestCallbackResponse callback) {
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
        
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest not found: " + backtestId));
            
            backtest.updateStatus(BacktestStatus.FAILED);
            backtestRepository.save(backtest);
            
            log.info("Updated backtest status to FAILED: backtestId={}, jobId={}, error={}", 
                    backtestId, callback.jobId(), callback.errorMessage());
                    
        } catch (Exception e) {
            log.error("Failed to update backtest status to FAILED: backtestId={}, jobId={}", 
                    backtestId, callback.jobId(), e);
        }
    }

    /**
     * 백테스트 성공 이벤트 처리
     */
    @EventListener
    @Transactional(propagation = REQUIRES_NEW)
    public void handleBacktestSuccessEvent(BacktestSuccessEvent event) {
        log.info("Handling backtest success event for backtestId: {}", event.backtestId());
        
        try {
            // 백테스트 상태를 완료로 업데이트
            updateBacktestStatus(event.backtestId(), BacktestStatus.COMPLETED);
            
            // 상세 데이터 저장 (분석용)
            BacktestExecutionResponse executionResponse = event.callback().toBacktestExecutionResponse();
            if (executionResponse != null) {
                saveDetailedBacktestResults(event.backtestId(), executionResponse);
            } else {
                log.warn("Cannot convert callback to BacktestExecutionResponse for backtestId: {}", event.backtestId());
            }
            
            log.info("Backtest completed successfully: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId());
                    
        } catch (Exception e) {
            log.error("Failed to process backtest success event: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId(), e);
        }
    }

    /**
     * 백테스트 실패 이벤트 처리
     */
    @EventListener
    @Transactional(propagation = REQUIRES_NEW)
    public void handleBacktestFailure(BacktestFailureEvent event) {
        log.info("Handling backtest failure event for backtestId: {}", event.backtestId());
        updateBacktestStatus(event.backtestId(), BacktestStatus.FAILED);
    }

    /**
     * 백테스트 상태 업데이트 (동기)
     */
    @Transactional(propagation = REQUIRES_NEW)
    public void updateBacktestStatus(Long backtestId, BacktestStatus status) {
        log.info("Updating backtest status to {} for backtestId: {}", status, backtestId);
        
        Backtest backtest = backtestRepository.findById(backtestId)
            .orElseThrow(() -> new ResourceNotFoundException("Backtest not found with id: " + backtestId));
        backtest.updateStatus(status);
        backtestRepository.save(backtest);
        
        log.info("Successfully updated backtest status to {} for backtestId: {}", status, backtestId);
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
            
            // 1단계: PostgreSQL 작업을 별도 트랜잭션에서 처리
            Long portfolioSnapshotId = savePostgresDataInTransaction(backtestId, response, null);
            log.info("Step 1: Saved PostgreSQL data with portfolioSnapshotId: {}", portfolioSnapshotId);
            
            // 2단계: MongoDB 작업을 트랜잭션 밖에서 동기적으로 처리
            try {
                mongoBacktestMetricsService.saveMetricsSync(response.metrics(), portfolioSnapshotId);
                log.info("Step 2: Saved MongoDB metrics synchronously for portfolioSnapshotId: {}", portfolioSnapshotId);
            } catch (Exception e) {
                log.error("Failed to save MongoDB metrics synchronously for portfolioSnapshotId: {}", portfolioSnapshotId, e);
                // MongoDB 실패 시 PostgreSQL 데이터 완전 정리 (비동기)
                mongoBacktestMetricsService.cleanupPostgresDataAsync(portfolioSnapshotId, backtestId);
                log.info("Step 2-1: Started PostgreSQL cleanup for portfolioSnapshotId: {}, backtestId: {}", portfolioSnapshotId, backtestId);
            }
            
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
        
        // 1단계: PostgreSQL에 포트폴리오 스냅샷 저장
        Long portfolioSnapshotId = savePortfolioSnapshot(backtest, response.portfolioSnapshot(), metricId);
        log.info("Step 1: Saved portfolio snapshot with ID: {}", portfolioSnapshotId);
        
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
     * PostgreSQL에 포트폴리오 스냅샷 저장
     * backtest 객체를 직접 전달받아 DB 조회 최적화
     */
    private Long savePortfolioSnapshot(Backtest backtest,
                                     BacktestExecutionResponse.PortfolioSnapshotResponse snapshotResponse,
                                     String metricId) {
        
        PortfolioSnapshot portfolioSnapshot = PortfolioSnapshot.create(
                backtest.getId(),        // portfolio_id 대신 backtest_id 사용
                snapshotResponse.baseValue(),
                snapshotResponse.currentValue(),
                metricId, // MongoDB에서 이미 저장된 metricId 사용
                snapshotResponse.startAt() != null ? snapshotResponse.startAt() : backtest.getStartAt(),
                snapshotResponse.endAt() != null ? snapshotResponse.endAt() : backtest.getEndAt(),
                snapshotResponse.executionTime()
        );
        
        PortfolioSnapshot savedSnapshot = snapshotRepository.savePortfolioSnapshot(portfolioSnapshot);
        
        log.debug("Created portfolio snapshot: portfolioId={}, baseValue={:.2f}, currentValue={:.2f}", 
                 backtest.getPortfolioId(), snapshotResponse.baseValue(), snapshotResponse.currentValue());
        
        return savedSnapshot.id();
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
     * MongoDB에 성과 지표를 동기적으로 저장 (트랜잭션 밖에서)
     * 성공 시에만 metricId 반환, 실패 시 예외 발생
     */
    @Async("backgroundTaskExecutor")
    public CompletableFuture<String> saveMongoMetricsAsync(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse) {
        try {
            BacktestMetricsDocument metricsDoc = new BacktestMetricsDocument(
                    null, // portfolioSnapshotId는 나중에 업데이트
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
            log.info("Saved MongoDB metrics with ID: {}", savedMetrics.getId());
            return CompletableFuture.completedFuture(savedMetrics.getId());
            
        } catch (Exception e) {
            log.error("Failed to save MongoDB metrics: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * MongoDB에 성과 지표를 동기적으로 저장 (트랜잭션 밖에서)
     * 성공 시에만 metricId 반환, 실패 시 예외 발생
     */
    private String saveMongoMetricsSync(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse) {
        try {
            CompletableFuture<String> future = saveMongoMetricsAsync(metricsResponse);
            return future.get(); // 동기적으로 대기
        } catch (Exception e) {
            log.error("Failed to save MongoDB metrics synchronously: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save MongoDB metrics", e);
        }
    }

    /**
     * MongoDB 메트릭에 portfolio_snapshot_id를 원자적으로 업데이트
     */
    @Async("backgroundTaskExecutor")
    public void updateMongoMetricsWithSnapshotIdAsync(String metricId, Long portfolioSnapshotId) {
        try {
            BacktestMetricsDocument updatedMetrics = new BacktestMetricsDocument();
            updatedMetrics.setId(metricId);
            updatedMetrics.setPortfolioSnapshotId(portfolioSnapshotId);
            updatedMetrics.setUpdatedAt(java.time.LocalDateTime.now());
            
            // MongoDB의 upsert 또는 직접 업데이트 사용
            backtestMetricsRepository.save(updatedMetrics);
            log.info("Successfully updated MongoDB metrics atomically: metricId={}, portfolioSnapshotId={}", 
                    metricId, portfolioSnapshotId);
                    
        } catch (Exception e) {
            log.error("Failed to update MongoDB metrics atomically: metricId={}, portfolioSnapshotId={}, error={}", 
                     metricId, portfolioSnapshotId, e.getMessage(), e);
        }
    }

    /**
     * MongoDB에 성과 지표를 완전히 비동기로 저장 (트랜잭션 밖에서)
     */
    @Async("backgroundTaskExecutor")
    public void saveMongoMetricsWithSnapshotIdAsync(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse, 
                                                   Long portfolioSnapshotId) {
        try {
            BacktestMetricsDocument metricsDoc = new BacktestMetricsDocument(
                    portfolioSnapshotId, // portfolioSnapshotId를 바로 설정
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
     * MongoDB 메트릭 롤백
     */
    private void rollbackMongoMetrics(String metricId) {
        try {
            backtestMetricsRepository.deleteById(metricId);
            log.info("Rolled back MongoDB metrics with ID: {}", metricId);
        } catch (Exception e) {
            log.error("Failed to rollback MongoDB metrics with ID: {}", metricId, e);
        }
    }

}
