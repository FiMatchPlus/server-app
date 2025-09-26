package com.stockone19.backend.backtest.controller;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.dto.CreateBacktestRequest;
import com.stockone19.backend.backtest.dto.CreateBacktestResult;
import com.stockone19.backend.backtest.dto.BacktestResponse;
import com.stockone19.backend.backtest.dto.BacktestResponseMapper;
import com.stockone19.backend.backtest.dto.BacktestDetailResponse;
import com.stockone19.backend.backtest.service.BacktestService;
import com.stockone19.backend.backtest.service.BacktestQueryService;
import com.stockone19.backend.backtest.service.BacktestExecutionService;
import com.stockone19.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.stockone19.backend.backtest.dto.BacktestCallbackResponse;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/backtests")
public class BacktestController {

    private final BacktestService backtestService;
    private final BacktestQueryService backtestQueryService;
    private final BacktestExecutionService backtestExecutionService;
    private final BacktestResponseMapper backtestResponseMapper;

    /**
     * 백테스트 생성
     * <ul>
     *     <li>백테스트 기본 정보 (제목, 설명, 기간)</li>
     *     <li>매매 규칙 (손절, 익절 전략) - 선택사항</li>
     *     <li>규칙이 있는 경우 MongoDB에 저장 후 rule_id 연결</li>
     * </ul>
     */
    @PostMapping("/portfolio/{portfolioId}")
    public ApiResponse<CreateBacktestResult> createBacktest(
            @PathVariable Long portfolioId,
            @Valid @RequestBody CreateBacktestRequest request) {
        
        log.info("POST /api/backtests/portfolio/{} - title: {}", portfolioId, request.title());
        
        // 시작일과 종료일 검증
        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("종료일은 시작일보다 나중이어야 합니다.");
        }
        
        CreateBacktestResult result = backtestService.createBacktest(portfolioId, request);
        return ApiResponse.success("백테스트가 생성되었습니다", result);
    }

    /**
     * 포트폴리오별 백테스트 조회
     * <ul>
     *     <li>해당 포트폴리오의 모든 백테스트 목록 조회</li>
     *     <li>portfolio_snapshots 데이터 존재 여부로 상태 판단</li>
     *     <li>완료된 백테스트의 경우 지표와 일별 수익률 포함</li>
     * </ul>
     */
    @GetMapping("/portfolio/{portfolioId}")
    public ApiResponse<List<BacktestResponse>> getBacktestsByPortfolioId(@PathVariable Long portfolioId) {
        
        log.info("GET /api/backtests/portfolio/{}", portfolioId);
        
        List<Backtest> backtests = backtestService.getBacktestsByPortfolioId(portfolioId);
        List<BacktestResponse> responses = backtestResponseMapper.toResponseList(backtests);
        
        return ApiResponse.success("포트폴리오 백테스트 목록을 조회했습니다", responses);
    }

    /**
     * 백테스트 상세 정보 조회
     * <ul>
     *     <li>백테스트 ID로 상세 정보 조회</li>
     *     <li>성과 지표, 일별 평가액, 포트폴리오 보유 정보 포함</li>
     * </ul>
     */
    @GetMapping("/{backtestId}")
    public ApiResponse<BacktestDetailResponse> getBacktestDetail(@PathVariable Long backtestId) {
        
        log.info("GET /api/backtests/{}", backtestId);
        
        BacktestDetailResponse response = backtestQueryService.getBacktestDetail(backtestId);
        
        return ApiResponse.success("백테스트 상세 조회 성공", response);
    }

    /**
     * 포트폴리오별 백테스트 상태 조회
     * <ul>
     *     <li>해당 포트폴리오의 모든 백테스트 ID와 상태만 조회</li>
     *     <li>Map 형태로 반환 (백테스트 ID -> 상태)</li>
     * </ul>
     */
    @GetMapping("/portfolios/{portfolioId}/status")
    public ApiResponse<Map<String, String>> getBacktestStatusesByPortfolioId(@PathVariable Long portfolioId) {
        
        log.info("GET /api/backtests/portfolios/{}/status", portfolioId);
        
        Map<String, String> backtestStatuses = backtestService.getBacktestStatusesByPortfolioId(portfolioId);
        
        return ApiResponse.success("포트폴리오 백테스트 상태 목록을 조회했습니다", backtestStatuses);
    }

    /**
     * 백테스트 실행 (백그라운드 작업)
     * <ul>
     *     <li>즉시 작업 ID 반환</li>
     *     <li>클라이언트가 페이지를 떠나도 작업 계속 진행</li>
     *     <li>SSE로 실시간 상태 확인 가능</li>
     * </ul>
     */
    @PostMapping("/{backtestId}/execute")
    public ResponseEntity<ApiResponse<String>> executeBacktest(@PathVariable Long backtestId) {
        log.info("POST /api/backtests/{}/execute - 백그라운드 작업으로 시작", backtestId);

        // todo: 회원 처리
        // Long userId = 1L;
        
        // 백테스트 실행 시작 (상태 업데이트 포함)
        backtestExecutionService.startBacktest(backtestId);
        
        return ResponseEntity.ok(ApiResponse.success(
            "백테스트 실행이 시작되었습니다", 
            backtestId.toString()
        ));
    }

    /**
     * 백테스트 엔진에서 콜백 수신
     */
    @PostMapping("/callback")
    public ResponseEntity<Object> handleBacktestCallback(
            @RequestBody BacktestCallbackResponse callback,
            HttpServletRequest request) {
        
        String clientIP = getClientIP(request);
        log.info("Backtest callback received from IP: {}, jobId: {}, success: {}", 
                clientIP, callback.jobId(), callback.success());
        
        try {
            if (Boolean.TRUE.equals(callback.success())) {
                // 성공 처리
                backtestExecutionService.handleBacktestSuccessCallback(callback);
            } else {
                // 실패 처리
                backtestExecutionService.handleBacktestFailure(callback);
            }
            return ResponseEntity.ok().build();
        } catch (Exception error) {
            log.error("Error processing backtest callback for jobId: {}", callback.jobId(), error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
