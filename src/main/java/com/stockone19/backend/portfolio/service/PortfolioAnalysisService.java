package com.stockone19.backend.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockone19.backend.portfolio.dto.PortfolioAnalysisResponse;
import com.stockone19.backend.portfolio.event.PortfolioAnalysisSuccessEvent;
import com.stockone19.backend.portfolio.event.PortfolioAnalysisFailureEvent;
import com.stockone19.backend.portfolio.event.PortfolioCreatedEvent;
import com.stockone19.backend.ai.service.PortfolioReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CompletableFuture;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

/**
 * 포트폴리오 분석 결과 처리 서비스
 * 포트폴리오 최적화 분석 결과를 받아서 처리하고 리포트 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioAnalysisService {

    private final PortfolioReportService portfolioReportService;
    private final PortfolioService portfolioService;
    private final ObjectMapper objectMapper;
    private final PortfolioAnalysisEngineClient portfolioAnalysisEngineClient;

    /**
     * 포트폴리오 생성 완료 이벤트 처리 (트랜잭션 커밋 후 실행)
     */
    @Async("backgroundTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePortfolioCreated(PortfolioCreatedEvent event) {
        Long portfolioId = event.getPortfolioId();
        log.info("Starting portfolio analysis for portfolioId: {}", portfolioId);

        try {
            // 포트폴리오 상태를 RUNNING으로 업데이트
            portfolioService.updatePortfolioStatus(portfolioId, com.stockone19.backend.portfolio.domain.Portfolio.PortfolioStatus.RUNNING);

            // 분석 엔진에 요청
            portfolioAnalysisEngineClient.submitToPortfolioAnalysisEngineAsync(portfolioId);

        } catch (Exception e) {
            log.error("Failed to start portfolio analysis for portfolioId: {}", portfolioId, e);
            // 분석 시작 실패 시 상태를 FAILED로 업데이트
            portfolioService.updatePortfolioStatus(portfolioId, com.stockone19.backend.portfolio.domain.Portfolio.PortfolioStatus.FAILED);
        }
    }

    /**
     * 포트폴리오 분석 수동 실행
     */
    @Transactional
    public void startPortfolioAnalysis(Long portfolioId) {
        log.info("Manually starting portfolio analysis for portfolioId: {}", portfolioId);

        // 상태를 RUNNING으로 업데이트
        portfolioService.updatePortfolioStatus(portfolioId, com.stockone19.backend.portfolio.domain.Portfolio.PortfolioStatus.RUNNING);

        // 비동기로 분석 시작
        portfolioAnalysisEngineClient.submitToPortfolioAnalysisEngineAsync(portfolioId);
    }

    /**
     * 포트폴리오 분석 성공 이벤트 처리
     */
    @EventListener
    @Async("backgroundTaskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public CompletableFuture<Void> handlePortfolioAnalysisSuccess(PortfolioAnalysisSuccessEvent event) {
        log.info("Processing portfolio analysis success - portfolioId: {}, analysisId: {}", 
                event.getPortfolioId(), event.getAnalysisId());
        
        try {
            PortfolioAnalysisResponse analysisResponse = event.getAnalysisResponse();
            
            // 1. 분석 결과 로깅
            logAnalysisResult(analysisResponse);
            
            // 2. 분석 결과를 JSON으로 변환하여 저장
            String analysisResultJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(analysisResponse);
            
            // 3. 포트폴리오 결과 저장 (현재 트랜잭션에서)
            savePortfolioAnalysisResult(event.getPortfolioId(), analysisResultJson);
            
            log.info("Portfolio analysis processing completed - portfolioId: {}, analysisId: {}", 
                    event.getPortfolioId(), event.getAnalysisId());
            
        } catch (Exception e) {
            log.error("Failed to process portfolio analysis success - portfolioId: {}, analysisId: {}", 
                    event.getPortfolioId(), event.getAnalysisId(), e);
            
            // 분석 처리 실패 시 포트폴리오 상태를 FAILED로 업데이트
            try {
                portfolioService.updatePortfolioStatus(event.getPortfolioId(), 
                        com.stockone19.backend.portfolio.domain.Portfolio.PortfolioStatus.FAILED);
            } catch (Exception updateException) {
                log.error("Failed to update portfolio status to FAILED - portfolioId: {}", 
                        event.getPortfolioId(), updateException);
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 포트폴리오 분석 실패 이벤트 처리
     */
    @EventListener
    @Async("backgroundTaskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public CompletableFuture<Void> handlePortfolioAnalysisFailure(PortfolioAnalysisFailureEvent event) {
        log.error("Portfolio analysis failed - portfolioId: {}, analysisId: {}, error: {}", 
                event.getPortfolioId(), event.getAnalysisId(), event.getErrorMessage());
        
        try {
            // 포트폴리오 상태를 FAILED로 업데이트
            portfolioService.updatePortfolioStatus(event.getPortfolioId(), 
                    com.stockone19.backend.portfolio.domain.Portfolio.PortfolioStatus.FAILED);
            
        } catch (Exception e) {
            log.error("Failed to update portfolio status to FAILED - portfolioId: {}", 
                    event.getPortfolioId(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 분석 결과 로깅
     */
    private void logAnalysisResult(PortfolioAnalysisResponse analysisResponse) {
        try {
            String analysisJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(analysisResponse);
            log.info("Portfolio analysis result: {}", analysisJson);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize analysis result for logging: {}", e.getMessage());
        }
    }

    /**
     * 포트폴리오 분석 결과 저장
     */
    private void savePortfolioAnalysisResult(Long portfolioId, String analysisResultJson) {
        try {
            portfolioService.savePortfolioAnalysisResult(portfolioId, analysisResultJson);
            log.info("Portfolio analysis result saved successfully - portfolioId: {}", portfolioId);
            
        } catch (Exception e) {
            log.error("Failed to save portfolio analysis result - portfolioId: {}", portfolioId, e);
            throw e; // 트랜잭션 롤백을 위해 예외 재발생
        }
    }
}