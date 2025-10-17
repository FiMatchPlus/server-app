package com.fimatchplus.backend.backtest.service;

import com.fimatchplus.backend.backtest.dto.BacktestCallbackResponse;
import com.fimatchplus.backend.backtest.event.BacktestFailureEvent;
import com.fimatchplus.backend.backtest.event.BacktestSuccessEvent;
import com.fimatchplus.backend.ai.service.BacktestReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestExecutionService {

    private final BacktestStatusManager backtestStatusManager;
    private final BacktestEngineClient backtestEngineClient;
    private final BacktestDataPersistenceService dataPersistenceService;
    private final BacktestReportService backtestReportService;

    /**
     * 백테스트 실행 시작
     *
     * @param backtestId 백테스트 ID
     * @return 비동기 실행 결과 (클라이언트가 대기 가능)
     */
    @Transactional
    public CompletableFuture<Void> startBacktest(Long backtestId) {
        log.info("Starting backtest execution for backtestId: {}", backtestId);
        
        backtestStatusManager.setBacktestStatusToRunning(backtestId);
        
        return backtestEngineClient.submitToBacktestEngineAsync(backtestId);
    }



    /**
     * 백테스트 성공 이벤트 처리
     */
    @EventListener
    @Async("backgroundTaskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public void handleBacktestSuccessEvent(BacktestSuccessEvent event) {
        log.info("=== Backtest Success Event Processing Started ===");
        log.info("Backtest ID: {}, Job ID: {}", event.backtestId(), event.callback().jobId());
        log.info("Callback received at: {}", event.callback().timestamp());
        
        try {
            handleBacktestSuccess(event.backtestId(), event.callback());
            
            generateReportSync(event.backtestId());
            
            log.info("=== Backtest Success Event Processing Completed ===");
            log.info("Backtest completed successfully: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId());
                    
        } catch (Exception e) {
            log.error("=== Backtest Success Event Processing Failed ===");
            log.error("Failed to process backtest success event: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId(), e);
            
            backtestStatusManager.setBacktestStatusToFailed(event.backtestId());
            log.info("Updated backtest status to FAILED due to processing error: backtestId={}", event.backtestId());
        }
    }

    /**
     * 백테스트 성공 처리 로직
     */
    private void handleBacktestSuccess(Long backtestId, BacktestCallbackResponse callback) {
        log.info("Starting backtest success processing for backtestId: {}", backtestId);
        
        // 콜백 데이터 요약 로그
        if (callback.portfolioSnapshot() != null) {
            var snapshot = callback.portfolioSnapshot();
            log.info("Portfolio snapshot - Base: {}, Current: {}, Holdings: {}", 
                    snapshot.baseValue(), snapshot.currentValue(), 
                    snapshot.holdings() != null ? snapshot.holdings().size() : 0);
        }
        
        if (callback.executionLogs() != null) {
            log.info("Execution logs count: {}", callback.executionLogs().size());
        }
        
        if (callback.resultSummary() != null) {
            log.info("Result summary days: {}", callback.resultSummary().size());
        }
        
        Long portfolioSnapshotId = null;
        
        try {
            log.info("Saving JPA data for backtestId: {}", backtestId);
            portfolioSnapshotId = dataPersistenceService.saveJpaDataInTransaction(backtestId, callback);
            log.info("JPA data saved successfully, portfolioSnapshotId: {}", portfolioSnapshotId);
            
            log.info("Saving JDBC batch data for portfolioSnapshotId: {}", portfolioSnapshotId);
            dataPersistenceService.saveJdbcDataInTransaction(portfolioSnapshotId, callback);
            log.info("JDBC batch data saved successfully");
            
            log.info("Updating backtest status to COMPLETED for backtestId: {}", backtestId);
            backtestStatusManager.setBacktestStatusToCompleted(backtestId);
            
            log.info("Successfully processed backtest completion for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to process backtest success for backtestId: {}", backtestId, e);
            
            if (portfolioSnapshotId != null) {
                try {
                    log.info("Attempting to rollback JPA data for backtestId: {}, portfolioSnapshotId: {}", 
                            backtestId, portfolioSnapshotId);
                    dataPersistenceService.rollbackJpaData(backtestId, portfolioSnapshotId);
                    log.info("JPA data rollback completed");
                } catch (Exception rollbackException) {
                    log.error("Failed to rollback JPA data for backtestId: {}", backtestId, rollbackException);
                }
            }
            
            log.info("Setting backtest status to FAILED for backtestId: {}", backtestId);
            backtestStatusManager.setBacktestStatusToFailed(backtestId);
            
            throw new RuntimeException("Failed to process backtest completion", e);
        }
    }

    /**
     * 백테스트 실패 이벤트 처리
     */
    @EventListener
    @Async("backgroundTaskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public void handleBacktestFailure(BacktestFailureEvent event) {
        log.info("Handling backtest failure event for backtestId: {}, errorMessage: {}", 
                event.backtestId(), event.errorMessage());
        
        try {
            backtestStatusManager.setBacktestStatusToFailed(event.backtestId());
            log.info("Successfully updated backtest status to FAILED for backtestId: {}", event.backtestId());
        } catch (Exception e) {
            log.error("Failed to update backtest status to FAILED for backtestId: {}", event.backtestId(), e);
        }
    }

    /**
     * 백테스트 완료 후 레포트 생성 (동기 처리)
     */
    public void generateReportSync(Long backtestId) {
        try {
            log.info("Starting report generation for backtestId: {}", backtestId);
            
            String backtestData = backtestReportService.getBacktestData(backtestId);
            
            String report = backtestReportService.generateAndSaveReport(backtestId, backtestData);
            
            log.info("Report generated successfully for backtestId: {}, length: {} characters", 
                    backtestId, report.length());
            
        } catch (Exception e) {
            log.error("Failed to generate report for backtestId: {}", backtestId, e);
        }
    }
}



