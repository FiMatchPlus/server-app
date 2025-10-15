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
        log.info("Handling backtest success event for backtestId: {}", event.backtestId());
        
        try {
            handleBacktestSuccess(event.backtestId(), event.callback());
            
            generateReportSync(event.backtestId());
            
            log.info("Backtest completed successfully: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId());
                    
        } catch (Exception e) {
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
        Long portfolioSnapshotId = null;
        
        try {
            portfolioSnapshotId = dataPersistenceService.saveJpaDataInTransaction(backtestId, callback);
            
            dataPersistenceService.saveJdbcDataInTransaction(portfolioSnapshotId, callback);
            
            backtestStatusManager.setBacktestStatusToCompleted(backtestId);
            
            log.info("Successfully processed backtest completion for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to process backtest success for backtestId: {}", backtestId, e);
            
            if (portfolioSnapshotId != null) {
                try {
                    dataPersistenceService.rollbackJpaData(backtestId, portfolioSnapshotId);
                } catch (Exception rollbackException) {
                    log.error("Failed to rollback JPA data for backtestId: {}", backtestId, rollbackException);
                }
            }
            
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



