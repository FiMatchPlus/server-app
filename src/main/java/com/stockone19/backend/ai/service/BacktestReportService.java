package com.stockone19.backend.ai.service;

import com.stockone19.backend.ai.dto.BacktestReportRequest;
import com.stockone19.backend.ai.dto.BacktestReportResponse;
import com.stockone19.backend.backtest.service.BacktestQueryService;
import com.stockone19.backend.backtest.dto.BacktestDetailResponse;
import com.stockone19.backend.backtest.dto.BacktestMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 백테스트 레포트 생성 서비스
 * AI를 활용하여 백테스트 결과를 바탕으로 분석 레포트를 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestReportService {

    private final ReportAIService reportAIService;
    private final BacktestQueryService backtestQueryService;

    /**
     * 백테스트 결과를 바탕으로 분석 레포트 생성
     * 
     * @param request 레포트 생성 요청
     * @return 분석 레포트 응답
     */
    public BacktestReportResponse generateReport(BacktestReportRequest request) {
        log.info("Generating backtest report for backtestId: {}", request.backtestId());
        
        try {
            // 백테스트 데이터 조회 및 분석 데이터 준비
            String backtestData = getBacktestData(request.backtestId());
            
            // TODO: 분석 초점이 지정된 경우 프롬프트 커스터마이징
            String analysisPrompt = buildAnalysisPrompt(backtestData, request.analysisFocus());
            
            // AI 크레이트를 사용하여 레포트 생성
            String report = reportAIService.generateResponse(analysisPrompt);
            
            return BacktestReportResponse.of(request.backtestId(), report);
            
        } catch (Exception e) {
            log.error("Failed to generate backtest report for backtestId: {}", request.backtestId(), e);
            throw new RuntimeException("백테스트 레포트 생성에 실패했습니다.", e);
        }
    }

    /**
     * 백테스트 데이터 조회
     * BacktestQueryService를 통해 실제 백테스트 결과 데이터를 조회
     */
    private String getBacktestData(Long backtestId) {
        try {
            BacktestDetailResponse backtestDetail = backtestQueryService.getBacktestDetail(backtestId);
            
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append(String.format("백테스트명: %s\n", backtestDetail.name()));
            dataBuilder.append(String.format("실행기간: %s\n", backtestDetail.period()));
            dataBuilder.append(String.format("실행시간: %.2f초\n", backtestDetail.executionTime()));
            
            // 백테스트 성과 지표
            if (backtestDetail.metrics() != null) {
                dataBuilder.append("\n=== 성과 지표 ===\n");
                dataBuilder.append(formatMetrics(backtestDetail.metrics()));
            }
            
            // 포트폴리오 보유 현황
            if (!backtestDetail.holdings().isEmpty()) {
                dataBuilder.append("\n=== 포트폴리오 보유 현황 ===\n");
                dataBuilder.append(formatHoldings(backtestDetail.holdings()));
            }
            
            // 일별 평가액 변화 (최근 7일간)
            if (!backtestDetail.dailyEquity().isEmpty()) {
                dataBuilder.append("\n=== 최근 평가액 변화 ===\n");
                dataBuilder.append(formatDailyEquity(backtestDetail.dailyEquity()));
            }
            
            return dataBuilder.toString();
        } catch (Exception e) {
            log.error("Failed to fetch backtest data for backtestId: {}", backtestId, e);
            throw new RuntimeException("백테스트 데이터 조회에 실패했습니다.", e);
        }
    }

    /**
     * 백테스트 성과 지표 포맷팅
     */
    private String formatMetrics(BacktestMetrics metrics) {
        StringBuilder metricsBuilder = new StringBuilder();
        
        metricsBuilder.append(String.format("총 수익률: %.2f%%\n", metrics.totalReturn()));
        metricsBuilder.append(String.format("연환산 수익률: %.2f%%\n", metrics.annualizedReturn()));
        metricsBuilder.append(String.format("변동성: %.2f%%\n", metrics.volatility()));
        metricsBuilder.append(String.format("샤프 비율: %.2f\n", metrics.sharpeRatio()));
        metricsBuilder.append(String.format("최대 낙폭: %.2f%%\n", metrics.maxDrawdown()));
        metricsBuilder.append(String.format("승률: %.2f%%\n", metrics.winRate()));
        metricsBuilder.append(String.format("손익비: %.2f\n", metrics.profitLossRatio()));
        
        return metricsBuilder.toString();
    }

    /**
     * 포트폴리오 보유 현황 포맷팅
     */
    private String formatHoldings(List<BacktestDetailResponse.HoldingData> holdings) {
        return holdings.stream()
                .map(holding -> String.format("- %s: %d주", holding.stockName(), holding.quantity()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 일별 평가액 변화 포맷팅 (최근 데이터)
     */
    private String formatDailyEquity(List<BacktestDetailResponse.DailyEquityData> dailyEquity) {
        // 최근 7일간 데이터만 포맷팅
        List<BacktestDetailResponse.DailyEquityData> recentData = dailyEquity.stream()
                .skip(Math.max(0, dailyEquity.size() - 7))
                .collect(Collectors.toList());
                
        return recentData.stream()
                .map(data -> {
                    StringBuilder dayBuilder = new StringBuilder(String.format("%s:\n", data.date()));
                    for (Map.Entry<String, Double> entry : data.stocks().entrySet()) {
                        dayBuilder.append(String.format("  %s: %,.2f원\n", entry.getKey(), entry.getValue()));
                    }
                    return dayBuilder.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * AI 분석을 위한 프롬프트 구축
     */
    private String buildAnalysisPrompt(String backtestData, String analysisFocus) {
        StringBuilder promptBuilder = new StringBuilder("""
                다음 백테스트 결과를 분석하여 전문적인 투자 전략 리포트를 작성해주세요:

                """);
        
        promptBuilder.append(backtestData).append("\n\n");
        promptBuilder.append("""
                다음 항목들을 포함하여 종합적인 분석 리포트를 작성해주세요:
                - 백테스트 성과 요약 (수익률, 리스크 지표)
                - 매매 전략의 효과성 분석
                - 위험 관리 측면의 평가 
                - 시장 환경 대비 성과 분석
                - 전략 개선 방안 및 추천사항
                """);
        
        if (analysisFocus != null && !analysisFocus.trim().isEmpty()) {
            promptBuilder.append("\n특별히 다음 관점에서 분석해주세요: ").append(analysisFocus);
        }
        
        promptBuilder.append("\n\n전문적이고 실용적인 투자 분석 리포트를 작성해주세요.");
        
        return promptBuilder.toString();
    }
}
