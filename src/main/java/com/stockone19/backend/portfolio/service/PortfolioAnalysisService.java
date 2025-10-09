package com.stockone19.backend.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockone19.backend.portfolio.domain.Portfolio;
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
    private final PortfolioCommandService portfolioCommandService;
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
            portfolioCommandService.updatePortfolioStatus(portfolioId, Portfolio.PortfolioStatus.RUNNING);

            // 분석 엔진에 요청
            portfolioAnalysisEngineClient.submitToPortfolioAnalysisEngineAsync(portfolioId);

        } catch (Exception e) {
            log.error("Failed to start portfolio analysis for portfolioId: {}", portfolioId, e);
            // 분석 시작 실패 시 상태를 FAILED로 업데이트
            portfolioCommandService.updatePortfolioStatus(portfolioId, Portfolio.PortfolioStatus.FAILED);
        }
    }

    /**
     * 포트폴리오 분석 수동 실행
     */
    @Transactional
    public void startPortfolioAnalysis(Long portfolioId) {
        log.info("Manually starting portfolio analysis for portfolioId: {}", portfolioId);

        // 상태를 RUNNING으로 업데이트
        portfolioCommandService.updatePortfolioStatus(portfolioId, Portfolio.PortfolioStatus.RUNNING);

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
        log.info("Processing portfolio analysis success - portfolioId: {}", 
                event.getPortfolioId());
        
        try {
            PortfolioAnalysisResponse analysisResponse = event.getAnalysisResponse();
            
            // 1. 분석 결과 로깅
            logAnalysisResult(analysisResponse);
            
            // 2. 분석 결과를 JSON으로 변환하여 저장
            String analysisResultJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(analysisResponse);
            
            // 3. 포트폴리오 결과 저장 (현재 트랜잭션에서)
            savePortfolioAnalysisResult(event.getPortfolioId(), analysisResultJson);
            
            // 4. LLM을 사용하여 분석 리포트 생성 (비동기)
            generatePortfolioAnalysisReport(event.getPortfolioId(), analysisResultJson);
            
            log.info("Portfolio analysis processing completed - portfolioId: {}", 
                    event.getPortfolioId());
            
        } catch (Exception e) {
            log.error("Failed to process portfolio analysis success - portfolioId: {}", 
                    event.getPortfolioId(), e);
            
            // 분석 처리 실패 시 포트폴리오 상태를 FAILED로 업데이트
            try {
                portfolioCommandService.updatePortfolioStatus(event.getPortfolioId(), 
                        Portfolio.PortfolioStatus.FAILED);
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
        log.error("Portfolio analysis failed - portfolioId: {}, error: {}", 
                event.getPortfolioId(), event.getErrorMessage());
        
        try {
            // 포트폴리오 상태를 FAILED로 업데이트
            portfolioCommandService.updatePortfolioStatus(event.getPortfolioId(), 
                    Portfolio.PortfolioStatus.FAILED);
            
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
            portfolioCommandService.savePortfolioAnalysisResult(portfolioId, analysisResultJson);
            log.info("Portfolio analysis result saved successfully - portfolioId: {}", portfolioId);
            
        } catch (Exception e) {
            log.error("Failed to save portfolio analysis result - portfolioId: {}", portfolioId, e);
            throw e; // 트랜잭션 롤백을 위해 예외 재발생
        }
    }

    /**
     * 포트폴리오 분석 리포트 생성 (비동기)
     * 포트폴리오 생성 후 자동으로 호출되거나 수동으로 호출 가능
     */
    @Async("backgroundTaskExecutor")
    public CompletableFuture<String> generatePortfolioAnalysisReport(Long portfolioId, String analysisResultJson) {
        log.info("Generating portfolio analysis report for portfolioId: {}", portfolioId);
        
        try {
            // LLM을 사용하여 분석 리포트 생성
            String report = portfolioReportService.generateOptimizationInsightFromAnalysis(analysisResultJson);
            
            // DB에 레포트 저장
            portfolioCommandService.savePortfolioReportResult(portfolioId, report);
            
            log.info("Portfolio analysis report generated and saved successfully - portfolioId: {}, report length: {}", 
                    portfolioId, report.length());
            
            return CompletableFuture.completedFuture(report);
            
        } catch (Exception e) {
            log.error("Failed to generate portfolio analysis report for portfolioId: {}", portfolioId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 포트폴리오 분석 리포트 수동 생성
     * DB에서 저장된 분석 결과를 조회하여 LLM 리포트 생성 후 저장
     */
    public String generatePortfolioAnalysisReportFromDb(Long portfolioId) {
        log.info("Manually generating portfolio analysis report for portfolioId: {}", portfolioId);
        
        try {
            // DB에서 포트폴리오 분석 결과 조회
            Portfolio portfolio = portfolioCommandService.getPortfolioById(portfolioId);
            
            if (portfolio.analysisResult() == null || portfolio.analysisResult().trim().isEmpty()) {
                throw new RuntimeException("포트폴리오 분석 결과가 없습니다. 먼저 포트폴리오 분석을 실행해주세요.");
            }
            
            // LLM을 사용하여 분석 리포트 생성
            String report = portfolioReportService.generateOptimizationInsightFromAnalysis(portfolio.analysisResult());
            
            // DB에 레포트 저장
            portfolioCommandService.savePortfolioReportResult(portfolioId, report);
            
            log.info("Portfolio analysis report generated and saved successfully from DB - portfolioId: {}, report length: {}", 
                    portfolioId, report.length());
            
            return report;
            
        } catch (Exception e) {
            log.error("Failed to generate portfolio analysis report from DB for portfolioId: {}", portfolioId, e);
            throw new RuntimeException("포트폴리오 분석 리포트 생성에 실패했습니다.", e);
        }
    }

    /**
     * 포트폴리오 분석 결과를 직접 파라미터로 받아서 LLM 리포트 생성
     * 포트폴리오 생성 후 바로 호출하는 경우 사용
     */
    public String generatePortfolioAnalysisReportFromData(PortfolioAnalysisResponse analysisResponse) {
        log.info("Generating portfolio analysis report from direct data");
        
        try {
            // 분석 결과를 JSON으로 변환
            String analysisResultJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(analysisResponse);
            
            // LLM을 사용하여 분석 리포트 생성
            String report = portfolioReportService.generateOptimizationInsightFromAnalysis(analysisResultJson);
            
            log.info("Portfolio analysis report generated successfully from direct data, report length: {}", 
                    report.length());
            
            return report;
            
        } catch (Exception e) {
            log.error("Failed to generate portfolio analysis report from direct data", e);
            throw new RuntimeException("포트폴리오 분석 리포트 생성에 실패했습니다.", e);
        }
    }
}