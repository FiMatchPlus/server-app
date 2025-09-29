package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.dto.BacktestStatus;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

/**
 * 백테스트 상태 관리 서비스
 * 백테스트 상태 변경과 관련된 모든 로직을 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestStatusManager {

    private final BacktestRepository backtestRepository;

    /**
     * 백테스트 상태 업데이트
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
     * 백테스트 상태를 FAILED로 변경
     */
    public void setBacktestStatusToFailed(Long backtestId) {
        log.info("Setting backtest status to FAILED for backtestId: {}", backtestId);
        
        try {
            updateBacktestStatus(backtestId, BacktestStatus.FAILED);
            log.info("Successfully updated backtest status to FAILED for backtestId: {}", backtestId);
        } catch (Exception e) {
            log.error("Failed to update backtest status to FAILED for backtestId: {}", backtestId, e);
            throw e;
        }
    }

    /**
     * 백테스트 상태를 COMPLETED로 변경
     */
    public void setBacktestStatusToCompleted(Long backtestId) {
        log.info("Setting backtest status to COMPLETED for backtestId: {}", backtestId);
        
        try {
            updateBacktestStatus(backtestId, BacktestStatus.COMPLETED);
            log.info("Successfully updated backtest status to COMPLETED for backtestId: {}", backtestId);
        } catch (Exception e) {
            log.error("Failed to update backtest status to COMPLETED for backtestId: {}", backtestId, e);
            throw e;
        }
    }

    /**
     * 백테스트 상태를 RUNNING으로 변경
     */
    public void setBacktestStatusToRunning(Long backtestId) {
        log.info("Setting backtest status to RUNNING for backtestId: {}", backtestId);
        
        try {
            updateBacktestStatus(backtestId, BacktestStatus.RUNNING);
            log.info("Successfully updated backtest status to RUNNING for backtestId: {}", backtestId);
        } catch (Exception e) {
            log.error("Failed to update backtest status to RUNNING for backtestId: {}", backtestId, e);
            throw e;
        }
    }

    /**
     * 백테스트 상태 조회
     */
    public BacktestStatus getBacktestStatus(Long backtestId) {
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest not found with id: " + backtestId));
            
            return backtest.getStatus();
        } catch (Exception e) {
            log.error("Failed to get backtest status for backtestId: {}", backtestId, e);
            throw new RuntimeException("Failed to get backtest status", e);
        }
    }
}
