package com.fimatchplus.backend.portfolio.controller;
import com.fimatchplus.backend.portfolio.dto.PortfolioAnalysisResponse;
import com.fimatchplus.backend.portfolio.dto.PortfolioStatusResponse;
import com.fimatchplus.backend.portfolio.event.PortfolioAnalysisSuccessEvent;
import com.fimatchplus.backend.portfolio.event.PortfolioAnalysisFailureEvent;
import com.fimatchplus.backend.portfolio.service.PortfolioAnalysisService;
import com.fimatchplus.backend.portfolio.service.PortfolioQueryService;
import com.fimatchplus.backend.common.dto.ApiResponse;
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
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final PortfolioQueryService portfolioQueryService;

    /**
     * 포트폴리오 분석 엔진에서 콜백 수신
     * 백테스트 엔진의 포트폴리오 분석 결과를 처리
     */
    @PostMapping("/callback")
    public ResponseEntity<Object> handlePortfolioAnalysisCallback(
            @RequestBody PortfolioAnalysisResponse analysisResponse,
            HttpServletRequest request) {
        
        String clientIP = getClientIP(request);
        
        try {
            log.info("Portfolio analysis callback received - ip: {}, success: {}", clientIP, analysisResponse.success());
            Long portfolioId = analysisResponse.metadata() != null ? analysisResponse.metadata().portfolioId() : null;
            if (portfolioId == null) {
                log.error("Missing portfolioId in callback");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            if (Boolean.TRUE.equals(analysisResponse.success())) {
                PortfolioAnalysisSuccessEvent successEvent = new PortfolioAnalysisSuccessEvent(
                        portfolioId, analysisResponse);
                applicationEventPublisher.publishEvent(successEvent);
                
                log.info("Portfolio analysis success event published - portfolioId: {}", 
                        portfolioId);
            } else {
                PortfolioAnalysisFailureEvent failureEvent = new PortfolioAnalysisFailureEvent(
                        portfolioId, "Portfolio analysis failed");
                applicationEventPublisher.publishEvent(failureEvent);
                
                log.warn("Portfolio analysis failure event published - portfolioId: {}", 
                        portfolioId);
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception error) {
            log.error("Error processing portfolio analysis callback", error);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    /**
     * 포트폴리오 최적화 수동 실행
     * 포트폴리오 저장은 성공했지만 분석에 실패한 경우 수동으로 재실행
     */
    @PostMapping("/{portfolioId}/start")
    public ResponseEntity<ApiResponse<String>> startPortfolioAnalysisManually(@PathVariable Long portfolioId) {
        log.info("POST /api/portfolio-analysis/{}/start - 수동 분석 시작", portfolioId);
        
        try {
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
     * 포트폴리오 분석 리포트 수동 생성
     * DB에 저장된 분석 결과를 바탕으로 LLM 리포트 생성
     */
    @PostMapping("/{portfolioId}/report")
    public ResponseEntity<ApiResponse<String>> generatePortfolioAnalysisReport(@PathVariable Long portfolioId) {
        log.info("POST /api/portfolio-analysis/{}/report - 수동 리포트 생성", portfolioId);
        
        try {
            String report = portfolioAnalysisService.generatePortfolioAnalysisReportFromDb(portfolioId);
            
            return ResponseEntity.ok(ApiResponse.success(
                    "포트폴리오 분석 리포트를 생성했습니다", 
                    report
            ));
        } catch (Exception e) {
            log.error("Failed to generate portfolio analysis report for portfolioId: {}", portfolioId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("포트폴리오 분석 리포트 생성에 실패했습니다: " + e.getMessage()));
        }
    }

    /**
     * 포트폴리오 분석 상태 조회
     * 최적화 분석 진행 상태를 추적
     */
    @GetMapping("/{portfolioId}/status")
    public ResponseEntity<ApiResponse<PortfolioStatusResponse>> getPortfolioAnalysisStatus(@PathVariable Long portfolioId) {
        log.info("GET /api/portfolio-analysis/{}/status - 분석 상태 조회", portfolioId);
        
        try {
            PortfolioStatusResponse statusResponse = portfolioQueryService.getPortfolioStatus(portfolioId);
            
            return ResponseEntity.ok(ApiResponse.success(
                    "포트폴리오 분석 상태 조회 완료",
                    statusResponse
            ));
        } catch (Exception e) {
            log.error("Failed to get portfolio analysis status for portfolioId: {}", portfolioId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("포트폴리오 분석 상태 조회에 실패했습니다."));
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