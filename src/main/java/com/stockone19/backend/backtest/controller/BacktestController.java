package com.stockone19.backend.backtest.controller;

import com.stockone19.backend.backtest.dto.CreateBacktestRequest;
import com.stockone19.backend.backtest.dto.CreateBacktestResult;
import com.stockone19.backend.backtest.service.BacktestService;
import com.stockone19.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/backtests")
public class BacktestController {

    private final BacktestService backtestService;

    /**
     * 백테스트 생성
     * <ul>
     *     <li>백테스트 기본 정보 (제목, 설명, 기간)</li>
     *     <li>매매 규칙 (손절, 익절 전략) - 선택사항</li>
     *     <li>규칙이 있는 경우 MongoDB에 저장 후 rule_id 연결</li>
     * </ul>
     */
    @PostMapping("/{portfolioId}")
    public ApiResponse<CreateBacktestResult> createBacktest(
            @PathVariable Long portfolioId,
            @Valid @RequestBody CreateBacktestRequest request) {
        
        log.info("POST /api/backtests/{} - title: {}", portfolioId, request.title());
        
        // 시작일과 종료일 검증
        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("종료일은 시작일보다 나중이어야 합니다.");
        }
        
        CreateBacktestResult result = backtestService.createBacktest(portfolioId, request);
        return ApiResponse.success("백테스트가 생성되었습니다", result);
    }
}
