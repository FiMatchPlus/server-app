package com.stockone19.backend.backtest.controller;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.dto.CreateBacktestRequest;
import com.stockone19.backend.backtest.dto.CreateBacktestResult;
import com.stockone19.backend.backtest.dto.BacktestResponse;
import com.stockone19.backend.backtest.dto.BacktestResponseMapper;
import com.stockone19.backend.backtest.dto.BacktestSummary;
import com.stockone19.backend.backtest.dto.BacktestExecutionResponse;
import com.stockone19.backend.backtest.service.BacktestService;
import com.stockone19.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * </ul>
     */
    @PostMapping("/{backtestId}/execute")
    public Mono<ApiResponse<BacktestExecutionResponse>> executeBacktest(@PathVariable Long backtestId) {
        log.info("POST /api/backtests/{}/execute", backtestId);
        
        return backtestService.executeBacktestReactive(backtestId)
                .map(result -> ApiResponse.success("백테스트 실행이 완료되었습니다", result))
                .onErrorResume(error -> {
                    log.error("백테스트 실행 실패: backtestId={}, error={}", backtestId, error.getMessage());
                    return Mono.just(ApiResponse.error("백테스트 실행 실패: " + error.getMessage()));
                });
    }
}
