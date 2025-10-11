package com.stockone19.backend.portfolio.controller;

import com.stockone19.backend.common.dto.ApiResponse;
import com.stockone19.backend.portfolio.dto.*;
import com.stockone19.backend.portfolio.service.PortfolioAnalysisDetailService;
import com.stockone19.backend.portfolio.service.PortfolioCommandService;
import com.stockone19.backend.portfolio.service.PortfolioQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/portfolios")
public class PortfolioController {

    private final PortfolioCommandService portfolioCommandService;
    private final PortfolioQueryService portfolioQueryService;
    private final PortfolioAnalysisDetailService portfolioAnalysisDetailService;

    /**
     * 사용자 포트폴리오 통합 합계 정보 조회 (포트폴리오 페이지 헤더)
     * <ul>
     *     <li>총 자산 합계</li>
     *     <li>전일대비 수익 금액</li>
     *     <li>전일대비 수익률</li>
     * </ul>
     * */
    @GetMapping("/summary")
    public ApiResponse<PortfolioSummaryResponse> getPortfolioSummary() {
        Long userId = 1L; // 고정된 userId 값 (나중에 인증/인가 로직 추가 예정)
        log.info("GET /api/portfolios/summary - userId: {}", userId);

        PortfolioSummaryResponse response = portfolioQueryService.getPortfolioSummary(userId);
        return ApiResponse.success("포트폴리오 통합 정보를 조회합니다", response);
    }

    /**
     * 포트폴리오 생성
     * <ul>
     *     <li>포트폴리오 기본 정보 (이름, 설명, 총 자산)</li>
     *     <li>보유 종목 정보 (종목 코드, 이름, 수량, 가격, 비중 등)</li>
     *     <li>매매 규칙 (리밸런싱, 손절, 익절 전략)</li>
     * </ul>
     * */
    @PostMapping
    public ApiResponse<CreatePortfolioResult> createPortfolio(
            @Valid @RequestBody CreatePortfolioRequest request) {
        log.info("POST /api/portfolios - name: {}", request.name());
        Long userId = 1L; // 고정된 userId 값
        CreatePortfolioResult data = portfolioCommandService.createPortfolio(userId, request);
        return ApiResponse.success("새로운 포트폴리오를 생성합니다", data);
    }

    /**
     * 단일 대표 포트폴리오 요약 정보 조회 (메인 페이지용)
     * <ul>
     *     <li>포트폴리오 이름</li>
     *     <li>총 자산</li>
     *     <li>보유 종목별 이름, 비중, 전일대비등락률</li>
     *     <li>전일대비등락금액 합계</li>
     *     <li>전일대비등락률 (추가 필요)</li>
     * </ul>
     * */
    @GetMapping("/main")
    public ApiResponse<PortfolioShortResponse> getPortfolioShort() {
        Long userId = 1L; // 고정된 userId 값 (나중에 인증/인가 로직 추가 예정)
        log.info("GET /api/portfolios/main - userId: {}", userId);

        PortfolioShortResponse response = portfolioQueryService.getMainPortfolioShort(userId);
        return ApiResponse.success("대표 포트폴리오 조회", response);
    }

    /**
     * 단일 포트폴리오 상세 정보 조회 (포트폴리오 페이지용)
     * <ul>
     *     <li>포트폴리오 식별자</li>
     *     <li>보유 종목별 이름, 비중, 금액, 전일대비등락률</li>
     *     <li>전략 문서 id</li>
     * </ul>
     * */
    @GetMapping("/{portfolioId}/long")
    public ApiResponse<PortfolioLongResponse> getPortfolioLong(@PathVariable Long portfolioId) {
        log.info("GET /api/portfolios/{}/long", portfolioId);

        PortfolioLongResponse response = portfolioQueryService.getPortfolioLong(portfolioId);
        return ApiResponse.success("단일 포트폴리오 조회", response);
    }

    /**
     * 포트폴리오 분석 결과만 조회
     * <ul>
     *     <li>분석 상태 (PENDING, RUNNING, COMPLETED, FAILED)</li>
     *     <li>분석 결과 (전략별 위험도, 비중 등)</li>
     * </ul>
     * */
    @GetMapping("/{portfolioId}/analysis")
    public ApiResponse<PortfolioLongResponse.AnalysisDetail> getPortfolioAnalysis(@PathVariable Long portfolioId) {
        log.info("GET /api/portfolios/{}/analysis", portfolioId);

        PortfolioLongResponse.AnalysisDetail analysisDetail = portfolioQueryService.getPortfolioAnalysisDetail(portfolioId);
        return ApiResponse.success("포트폴리오 분석 결과를 조회합니다", analysisDetail);
    }

    /**
     * 포트폴리오 분석 상세 정보 조회 (리포트 포함)
     * <ul>
     *     <li>분석 상태 (PENDING, RUNNING, COMPLETED, FAILED)</li>
     *     <li>포트폴리오 이름</li>
     *     <li>분석 날짜</li>
     *     <li>분석 기간 (시작일, 종료일)</li>
     *     <li>결과 (user, min-variance, max-sharpe)</li>
     *     <li>각 포트폴리오별 위험도, 보유 비중, 성과지표, 강점, 약점</li>
     * </ul>
     * */
    @GetMapping("/{portfolioId}/detail")
    public ApiResponse<PortfolioAnalysisDetailResponse> getPortfolioAnalysisDetail(@PathVariable Long portfolioId) {
        log.info("GET /api/portfolios/{}/detail", portfolioId);

        PortfolioAnalysisDetailResponse response = portfolioAnalysisDetailService.getPortfolioAnalysisDetail(portfolioId);
        return ApiResponse.success("포트폴리오 분석 상세 정보를 조회합니다", response);
    }

    /**
     * 사용자 포트폴리오 리스트 조회 (포트폴리오 페이지용) - 포트폴리오 항목별 정보
     * <ul>
     *     <li>포트폴리오 제목</li>
     *     <li>포트폴리오 설명</li>
     *     <li>자산 합계</li>
     *     <li>전일대비등락금액 합계</li>
     *     <li>전일대비등락률 (추가 필요)</li>
     *     <li>보유 종목별 ticker, 이름, 비중</li>
     * </ul>
     * */
    @GetMapping
    public ApiResponse<PortfolioListResponse> getPortfolioList() {
        Long userId = 1L; // 고정된 userId 값
        log.info("GET /api/portfolios - userId: {}", userId);

        PortfolioListResponse response = portfolioQueryService.getPortfolioList(userId);
        return ApiResponse.success("사용자의 포트폴리오 목록을 조회합니다", response);
    }
}
