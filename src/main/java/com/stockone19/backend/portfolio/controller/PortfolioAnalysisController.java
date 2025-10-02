package com.stockone19.backend.portfolio.controller;

import com.stockone19.backend.portfolio.dto.PortfolioAnalysisResponse;
import com.stockone19.backend.portfolio.event.PortfolioAnalysisSuccessEvent;
import com.stockone19.backend.portfolio.event.PortfolioAnalysisFailureEvent;
import com.stockone19.backend.portfolio.service.PortfolioService;
import com.stockone19.backend.portfolio.service.PortfolioAnalysisEngineClient;
import com.stockone19.backend.portfolio.service.PortfolioAnalysisService;
import com.stockone19.backend.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 포트폴리오 분석 엔진 콜백 API 컨트롤러
 * 포트폴리오 최적화 분석 결과를 받아서 처리
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/portfolio-analysis")
public class PortfolioAnalysisController {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final PortfolioAnalysisEngineClient portfolioAnalysisEngineClient;
    private final PortfolioService portfolioService;
    private final PortfolioAnalysisService portfolioAnalysisService;

    /**
     * 포트폴리오 분석 엔진에서 콜백 수신
     * 백테스트 엔진의 포트폴리오 분석 결과를 처리
     */
    @PostMapping("/callback")
    public ResponseEntity<Object> handlePortfolioAnalysisCallback(
            @RequestBody PortfolioAnalysisResponse analysisResponse,
            HttpServletRequest request) {
        
        String clientIP = getClientIP(request);
        log.info("Portfolio analysis callback received from IP: {}, analysisId: {}, success: {}", 
                clientIP, analysisResponse.analysisId(), analysisResponse.success());
        
        try {
            // 분석 ID와 포트폴리오 ID 확인
            Long analysisId = analysisResponse.analysisId();
            Long portfolioId = analysisResponse.portfolioId();
            
            if (analysisId == null || portfolioId == null) {
                log.error("Missing analysisId or portfolioId in callback - analysisId: {}, portfolioId: {}", 
                        analysisId, portfolioId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            if (Boolean.TRUE.equals(analysisResponse.success())) {
                // 성공 처리 - 이벤트 발행
                PortfolioAnalysisSuccessEvent successEvent = new PortfolioAnalysisSuccessEvent(
                        portfolioId, analysisId, analysisResponse);
                applicationEventPublisher.publishEvent(successEvent);
                
                log.info("Portfolio analysis success event published - portfolioId: {}, analysisId: {}", 
                        portfolioId, analysisId);
            } else {
                // 실패 처리 - 이벤트 발행
                PortfolioAnalysisFailureEvent failureEvent = new PortfolioAnalysisFailureEvent(
                        portfolioId, analysisId, "Portfolio analysis failed");
                applicationEventPublisher.publishEvent(failureEvent);
                
                log.warn("Portfolio analysis failure event published - portfolioId: {}, analysisId: {}", 
                        portfolioId, analysisId);
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception error) {
            log.error("Error processing portfolio analysis callback for analysisId: {}", 
                    analysisResponse.analysisId(), error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 포트폴리오 분석 수동 실행
     * 포트폴리오 저장은 성공했지만 분석에 실패한 경우 수동으로 재실행
     */
    @PostMapping("/{portfolioId}/start")
    public ResponseEntity<ApiResponse<String>> startPortfolioAnalysisManually(@PathVariable Long portfolioId) {
        log.info("POST /api/portfolio-analysis/{}/start - 수동 분석 시작", portfolioId);
        
        try {
            // 포트폴리오 분석 수동 실행
            portfolioAnalysisService.startPortfolioAnalysis(portfolioId);
            
            return ResponseEntity.ok(ApiResponse.success(
                    "포트폴리오 분석을 수동으로 시작했습니다", 
                    "분석이 백그라운드에서 실행됩니다"
            ));
        } catch (Exception e) {
            log.error("Failed to start portfolio analysis manually for portfolioId: {}", portfolioId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("포트폴리오 분석 시작에 실패했습니다."));
        }
    }
    
    /**
     * 클라이언트 IP 추출
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }
}