package com.fimatchplus.backend.ai.controller;

import com.fimatchplus.backend.ai.dto.BacktestReportRequest;
import com.fimatchplus.backend.ai.dto.BacktestReportResponse;
import com.fimatchplus.backend.ai.service.BacktestReportService;
import com.fimatchplus.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 백테스트 레포트 생성 API 컨트롤러
 * AI가 백테스트 결과를 바탕으로 분석 레포트를 생성
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/reports/backtest")
public class BacktestReportController {

    private final BacktestReportService backtestReportService;

    /**
     * 백테스트 결과 기반 AI 분석 레포트 생성
     * <ul>
     *     <li>백테스트 실행 결과 데이터 분석</li>
     *     <li>수익률, 리스크, 전략 성과 평가</li>
     *     <li>개선 포인트 및 추천사항 제공</li>
     * </ul>
     */
    @PostMapping
    public ApiResponse<BacktestReportResponse> generateReport(
            @Valid @RequestBody BacktestReportRequest request) {
        
        log.info("POST /api/reports/backtest - backtestId: {}", request.backtestId());
        
        BacktestReportResponse response = backtestReportService.generateReport(request);
        
        return ApiResponse.success("백테스트 분석 레포트를 생성합니다", response);
    }

    /**
     * 백테스트 레포트 재생성
     * <ul>
     *     <li>기존 레포트를 새로운 레포트로 업데이트</li>
     * </ul>
     */
    @PostMapping("/{backtestId}/regenerate")
    public ApiResponse<BacktestReportResponse> regenerateReport(@PathVariable Long backtestId) {
        
        log.info("POST /api/reports/backtest/{}/regenerate", backtestId);
        
        BacktestReportRequest request = new BacktestReportRequest(backtestId, null);
        BacktestReportResponse response = backtestReportService.generateReport(request);
        
        return ApiResponse.success("백테스트 분석 레포트를 재생성합니다", response);
    }
}
