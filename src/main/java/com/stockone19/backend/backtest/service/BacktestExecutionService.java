package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.dto.BacktestCallbackResponse;
import com.stockone19.backend.backtest.event.BacktestFailureEvent;
import com.stockone19.backend.backtest.event.BacktestSuccessEvent;
import com.stockone19.backend.ai.service.BacktestReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

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
        
        // 백테스트 상태를 RUNNING으로 업데이트
        backtestStatusManager.setBacktestStatusToRunning(backtestId);
        
        // 백테스트 엔진에 비동기 요청 제출
        return backtestEngineClient.submitToBacktestEngineAsync(backtestId);
    }



    /**
     * 백테스트 성공 이벤트 처리
     */
    @EventListener
    @Transactional(propagation = REQUIRES_NEW)
    public void handleBacktestSuccessEvent(BacktestSuccessEvent event) {
        log.info("Handling backtest success event for backtestId: {}", event.backtestId());
        
        try {
            // 백테스트 성공 처리
            handleBacktestSuccess(event.backtestId(), event.callback());
            
            // 백테스트 완료 후 레포트 생성 (동기 처리)
            generateReportSync(event.backtestId());
            
            log.info("Backtest completed successfully: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId());
                    
        } catch (Exception e) {
            log.error("Failed to process backtest success event: backtestId={}, jobId={}", 
                    event.backtestId(), event.callback().jobId(), e);
            
            // 성공 이벤트 처리 중 실패 시 백테스트 상태를 FAILED로 변경
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
            // 1단계: JPA 데이터 저장 (핵심 데이터)
            portfolioSnapshotId = dataPersistenceService.saveJpaDataInTransaction(backtestId, callback);
            
            // 2단계: JDBC 배치 저장 (대량 데이터)
            dataPersistenceService.saveJdbcDataInTransaction(portfolioSnapshotId, callback);
            
            // 3단계: 백테스트 상태를 COMPLETED로 변경
            backtestStatusManager.setBacktestStatusToCompleted(backtestId);
            
            log.info("Successfully processed backtest completion for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to process backtest success for backtestId: {}", backtestId, e);
            
            // JDBC 실패 시 JPA 데이터 롤백
            if (portfolioSnapshotId != null) {
                try {
                    dataPersistenceService.rollbackJpaData(backtestId, portfolioSnapshotId);
                } catch (Exception rollbackException) {
                    log.error("Failed to rollback JPA data for backtestId: {}", backtestId, rollbackException);
                }
            }
            
            // 백테스트 상태를 FAILED로 변경
            backtestStatusManager.setBacktestStatusToFailed(backtestId);
            
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
            
            // 백테스트 데이터 조회 (이미 메모리에 있는 데이터 활용 가능)
            String backtestData = backtestReportService.getBacktestData(backtestId);
            
            // 레포트 생성 및 DB 저장
            String report = backtestReportService.generateAndSaveReport(backtestId, backtestData);
            
            log.info("Report generated successfully for backtestId: {}, length: {} characters", 
                    backtestId, report.length());
            
        } catch (Exception e) {
            log.error("Failed to generate report for backtestId: {}", backtestId, e);
            // 레포트 생성 실패는 백테스트 완료에 영향을 주지 않음
        }
    }
}



