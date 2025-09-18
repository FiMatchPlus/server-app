package com.stockone19.backend.backtest.controller;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.dto.CreateBacktestRequest;
import com.stockone19.backend.backtest.dto.CreateBacktestResult;
import com.stockone19.backend.backtest.dto.BacktestResponse;
import com.stockone19.backend.backtest.dto.BacktestResponseMapper;
import com.stockone19.backend.backtest.dto.BacktestSummary;
import com.stockone19.backend.backtest.dto.BacktestErrorResponse;
import com.stockone19.backend.backtest.exception.BacktestExecutionException;
import com.stockone19.backend.backtest.service.BacktestService;
import com.stockone19.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/backtests")
public class BacktestController {

    private final BacktestService backtestService;
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
        List<BacktestResponse> responses = backtestResponseMapper.toResponseList(backtests, portfolioId);
        
        return ApiResponse.success("포트폴리오 백테스트 목록을 조회했습니다", responses);
    }

    /**
     * 백테스트 상세 정보 조회
     * <ul>
     *     <li>백테스트 ID로 상세 정보 조회</li>
     *     <li>성과 지표, 일별 수익률 등 포함</li>
     * </ul>
     */
    @GetMapping("/{backtestId}")
    public ApiResponse<BacktestSummary> getBacktestDetail(@PathVariable Long backtestId) {
        
        log.info("GET /api/backtests/{}", backtestId);
        
        BacktestSummary summary = backtestService.getBacktestDetail(backtestId);
        
        return ApiResponse.success("백테스트 상세 정보를 조회했습니다", summary);
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
     * 백테스트 실행 (WebFlux)
     * <ul>
     *     <li>외부 백테스트 서버에 요청을 전송하여 백테스트 실행</li>
     *     <li>비동기 논블로킹 방식으로 처리</li>
     *     <li>기존 record 클래스들을 활용하여 결과 처리</li>
     *     <li>실패 시 구조화된 에러 응답 반환</li>
     * </ul>
     */
    @PostMapping("/{backtestId}/execute")
    public Mono<ResponseEntity<ApiResponse<Object>>> executeBacktest(@PathVariable Long backtestId) {
        log.info("POST /api/backtests/{}/execute", backtestId);
        
        return backtestService.executeBacktestReactive(backtestId)
                .map(result -> ResponseEntity.ok(ApiResponse.<Object>success("백테스트 실행이 완료되었습니다", result)))
                .onErrorResume(error -> {
                    log.error("백테스트 실행 실패: backtestId={}, error={}", backtestId, error.getMessage());
                    
                    // BacktestExecutionException인 경우 구조화된 에러 응답 반환
                    if (error instanceof BacktestExecutionException) {
                        BacktestExecutionException backtestException = (BacktestExecutionException) error;
                        BacktestErrorResponse errorResponse = backtestResponseMapper.toErrorResponse(backtestException);
                        
                        HttpStatus status = determineHttpStatus(backtestException.getErrorType());
                        ApiResponse<Object> response = ApiResponse.error(getErrorMessage(backtestException.getErrorType()), errorResponse);
                        
                        return Mono.just(ResponseEntity.status(status).body(response));
                    }
                    
                    // 기타 예외인 경우 일반적인 에러 응답
                    BacktestErrorResponse errorResponse = BacktestErrorResponse.createGeneralError(
                            "EXECUTION_ERROR",
                            "백테스트 실행 중 예상치 못한 오류가 발생했습니다: " + error.getMessage(),
                            null,
                            null
                    );
                    
                    ApiResponse<Object> response = ApiResponse.error("백테스트 실행 실패", errorResponse);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
                });
    }
    
    /**
     * 에러 타입에 따른 HTTP 상태 코드 결정
     */
    private HttpStatus determineHttpStatus(String errorType) {
        return switch (errorType) {
            case "MISSING_STOCK_PRICE_DATA", "INSUFFICIENT_DATA" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "INVALID_DATE_RANGE", "PORTFOLIO_EMPTY" -> HttpStatus.BAD_REQUEST;
            case "SERVER_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
    
    /**
     * 에러 타입에 따른 메시지 생성
     */
    private String getErrorMessage(String errorType) {
        return switch (errorType) {
            case "MISSING_STOCK_PRICE_DATA" -> "주가 데이터 부족으로 백테스트를 실행할 수 없습니다";
            case "INVALID_DATE_RANGE" -> "백테스트 기간이 올바르지 않습니다";
            case "INSUFFICIENT_DATA" -> "백테스트 실행에 필요한 데이터가 부족합니다";
            case "PORTFOLIO_EMPTY" -> "포트폴리오가 비어있습니다";
            case "SERVER_ERROR" -> "서버 오류가 발생했습니다";
            default -> "백테스트 실행에 실패했습니다";
        };
    }
}
