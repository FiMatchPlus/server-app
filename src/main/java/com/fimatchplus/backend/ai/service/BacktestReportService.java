package com.fimatchplus.backend.ai.service;

import com.fimatchplus.backend.ai.dto.BacktestReportRequest;
import com.fimatchplus.backend.ai.dto.BacktestReportResponse;
import com.fimatchplus.backend.backtest.service.BacktestQueryService;
import com.fimatchplus.backend.backtest.repository.BacktestRepository;
import com.fimatchplus.backend.backtest.repository.SnapshotRepository;
import com.fimatchplus.backend.backtest.repository.ExecutionLogJdbcRepository;
import com.fimatchplus.backend.backtest.domain.Backtest;
import com.fimatchplus.backend.backtest.domain.PortfolioSnapshot;
import com.fimatchplus.backend.backtest.domain.ExecutionLog;
import com.fimatchplus.backend.backtest.domain.ActionType;
import com.fimatchplus.backend.backtest.dto.BacktestDetailResponse;
import com.fimatchplus.backend.backtest.dto.BacktestMetrics;
import com.fimatchplus.backend.common.exception.ResourceNotFoundException;
import com.fimatchplus.backend.portfolio.domain.BenchmarkIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 백테스트 레포트 생성 서비스
 * AI를 활용하여 백테스트 결과를 바탕으로 분석 레포트를 생성
 * 실제 벤치마크 지수 비교 분석 포함
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestReportService {

    private final ReportAIService reportAIService;
    private final BacktestQueryService backtestQueryService;
    private final BacktestRepository backtestRepository;
    private final SnapshotRepository snapshotRepository;
    private final ExecutionLogJdbcRepository executionLogRepository;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    /**
     * 백테스트 결과를 바탕으로 분석 레포트 생성
     * 
     * @param request 레포트 생성 요청
     * @return 분석 레포트 응답
     */
    public BacktestReportResponse generateReport(BacktestReportRequest request) {
        log.info("Generating backtest report for backtestId: {}", request.backtestId());
        
        try {
            String backtestData = getBacktestData(request.backtestId());
            
            String analysisPrompt = promptTemplateService.buildBacktestReportPrompt(backtestData, request.analysisFocus());
            
            String report = reportAIService.generateResponse(analysisPrompt);
            
            return BacktestReportResponse.of(request.backtestId(), report);
            
        } catch (Exception e) {
            log.error("Failed to generate backtest report for backtestId: {}", request.backtestId(), e);
            throw new RuntimeException("백테스트 레포트 생성에 실패했습니다.", e);
        }
    }
    
    /**
     * 백테스트 완료 후 레포트 생성 및 DB 업데이트
     * 
     * @param backtestId 백테스트 ID
     * @param backtestData 백테스트 데이터 (이미 조회된 데이터)
     * @return 생성된 레포트 내용
     */
    public String generateAndSaveReport(Long backtestId, String backtestData) {
        log.info("Generating and saving backtest report for backtestId: {}", backtestId);
        
        try {
            String analysisPrompt = promptTemplateService.buildBacktestReportPrompt(backtestData, null);
            
            String report = reportAIService.generateResponse(analysisPrompt);
            
            saveReportToSnapshot(backtestId, report);
            
            log.info("Successfully generated and saved report for backtestId: {}", backtestId);
            return report;
            
        } catch (Exception e) {
            log.error("Failed to generate and save report for backtestId: {}", backtestId, e);
            throw new RuntimeException("백테스트 레포트 생성 및 저장에 실패했습니다.", e);
        }
    }

    /**
     * 백테스트 데이터 조회
     * 한 번의 조회로 필요한 모든 데이터를 수집하여 중복 접근 방지
     */
    public String getBacktestData(Long backtestId) {
        try {
            BacktestDetailResponse backtestDetail = backtestQueryService.getBacktestDetail(backtestId);
            
            Backtest benchmarkInfoBacktest = backtestRepository.findById(backtestId)
                    .orElseThrow(() -> new ResourceNotFoundException("백테스트를 찾을 수 없습니다: " + backtestId));
            
            PortfolioSnapshot latestSnapshotForMetrics = snapshotRepository.findLatestPortfolioSnapshotByBacktestId(backtestId);
            
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append(String.format("백테스트명: %s\n", backtestDetail.name()));
            dataBuilder.append(String.format("실행기간: %s\n", backtestDetail.period()));
            dataBuilder.append(String.format("실행시간: %.2f초\n", backtestDetail.executionTime()));
            
            String benchmarkAnalysis = extractBenchmarkInfo(backtestId, benchmarkInfoBacktest, latestSnapshotForMetrics);
            if (!benchmarkAnalysis.trim().isEmpty()) {
                dataBuilder.append(benchmarkAnalysis);
            }
            
            if (backtestDetail.metrics() != null) {
                dataBuilder.append("\n=== 성과 지표 ===\n");
                dataBuilder.append(formatMetrics(backtestDetail.metrics()));
            }
            
            if (!backtestDetail.holdings().isEmpty()) {
                dataBuilder.append("\n=== 포트폴리오 보유 현황 ===\n");
                dataBuilder.append(formatHoldings(backtestDetail.holdings()));
            }
            
            if (!backtestDetail.dailyEquity().isEmpty()) {
                dataBuilder.append("\n=== 포트폴리오 평가액 트렌드 분석 ===\n");
                dataBuilder.append(formatDailyEquity(backtestDetail.dailyEquity()));
            }
            
            List<ExecutionLog> executionLogs = executionLogRepository.findByBacktestId(backtestId);
            if (!executionLogs.isEmpty()) {
                dataBuilder.append("\n=== 거래 기록 ===\n");
                dataBuilder.append(formatExecutionLogs(executionLogs));
            }
            
            return dataBuilder.toString();
        } catch (Exception e) {
            log.error("Failed to fetch backtest data for backtestId: {}", backtestId, e);
            throw new RuntimeException("백테스트 데이터 조회에 실패했습니다.", e);
        }
    }

    /**
     * 벤치마크 정보 추출
     * 매개변수로 필요한 데이터 전달
     */
    private String extractBenchmarkInfo(Long backtestId, Backtest backtestFromQuery, PortfolioSnapshot snapshotForMetrics) {
        StringBuilder benchmarkInfo = new StringBuilder("\n=== 벤치마크 비교 분석 ===\n");
        
        try {
            String benchmarkCode = backtestFromQuery.getBenchmarkCode();
            
            if (benchmarkCode == null || benchmarkCode.trim().isEmpty()) {
                benchmarkInfo.append("벤치마크 지정됨 없음\n");
                benchmarkInfo.append("※ 이 백테스트는 특정 벤치마크와 비교하지 않습니다.\n\n");
                return benchmarkInfo.toString();
            }
            
            BenchmarkIndex benchmarkIndex = BenchmarkIndex.fromCode(benchmarkCode);
            String benchmarkName = benchmarkIndex != null ? benchmarkIndex.getName() : benchmarkCode;
            String benchmarkDescription = benchmarkIndex != null ? benchmarkIndex.getDescription() : "";
            
            benchmarkInfo.append(String.format("벤치마크 지수: %s\n", benchmarkCode));
            if (benchmarkIndex != null) {
                benchmarkInfo.append(String.format("→ %s: %s\n", benchmarkCode, benchmarkName));
                benchmarkInfo.append(String.format("설명: %s\n", benchmarkDescription));
            }
            
            String benchmarkAnalysis = extractBenchmarkFromMetricsOptimized(snapshotForMetrics);
            
            if (!benchmarkAnalysis.trim().isEmpty()) {
                benchmarkInfo.append(benchmarkAnalysis);
            } else {
                benchmarkInfo.append("※ 벤치마크 성과 데이터가 없습니다.\n\n");
            }
            
            return benchmarkInfo.toString();
            
        } catch (Exception e) {
            log.warn("벤치마크 정보 조회 중 오류 발생: {}", e.getMessage());
            benchmarkInfo.append("벤치마크 정보를 조회할 수 없습니다.\n\n");
            return benchmarkInfo.toString();
        }
    }

    /**
     * metrics JSON에서 벤치마크 데이터 추출
     * 이미 조회된 PortfolioSnapshot을 매개변수로 받아 추가 DB 조회 방지
     */
    private String extractBenchmarkFromMetricsOptimized(PortfolioSnapshot latestSnapshot) {
        StringBuilder analysis = new StringBuilder();
        
        try {
            if (latestSnapshot == null || latestSnapshot.metrics() == null) {
                return "";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> metricsMap = objectMapper.readValue(latestSnapshot.metrics(), Map.class);
            Object benchmarkObject = metricsMap.get("benchmark");
            
            if (benchmarkObject instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> benchmarkMap = (Map<String, Object>) benchmarkObject;
                
                Double benchmarkTotalReturn = getDoubleValue(benchmarkMap, "benchmark_total_return");
                Double benchmarkVolatility = getDoubleValue(benchmarkMap, "benchmark_volatility");
                Double benchmarkMaxPrice = getDoubleValue(benchmarkMap, "benchmark_max_price");
                Double benchmarkMinPrice = getDoubleValue(benchmarkMap, "benchmark_min_price");
                Double alpha = getDoubleValue(benchmarkMap, "alpha");
                Double benchmarkDailyAverage = getDoubleValue(benchmarkMap, "benchmark_daily_average");
                
                analysis.append("\n=== 벤치마크 성과 분석 ===\n");
                analysis.append(String.format("벤치마크 총 수익률: %.2f%%\n", benchmarkTotalReturn));
                analysis.append(String.format("벤치마크 변동성: %.2f%%\n", benchmarkVolatility));
                analysis.append(String.format("기간 최고가: %,.2f\n", benchmarkMaxPrice));
                analysis.append(String.format("기간 최저가: %,.2f\n", benchmarkMinPrice));
                analysis.append(String.format("초과수익률(Alpha): %.2f%%\n", alpha));
                if (benchmarkDailyAverage != null && benchmarkDailyAverage != 0) {
                    analysis.append(String.format("벤치마크 일일평균: %.3f%%\n", benchmarkDailyAverage));
                }
                
                if (alpha != null && alpha > 0) {
                    analysis.append("→ 포트폴리오가 벤치마크를 상회했습니다.\n");
                } else if (alpha != null && alpha < 0) {
                    analysis.append("→ 포트폴리오가 벤치마크에 미달했습니다.\n");
                } else {
                    analysis.append("→ 포트폴리오와 벤치마크 성과가 유사합니다.\n");
                }
                analysis.append("\n");
            }
            
            return analysis.toString();
            
        } catch (Exception e) {
            log.warn("저장된 벤치마크 메트릭스 파싱 실패: error={}", e.getMessage());
            return "";
        }
    }
    
    
    /**
     * JSON에서 Double 값 추출
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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
     * 트렌드 변화점 기반 일별 평가액 포맷팅
     * 상승/하락 전환점, 주요 변곡점, 지속된 경향성 분석
     */
    private String formatDailyEquity(List<BacktestDetailResponse.DailyEquityData> dailyEquity) {
        if (dailyEquity.isEmpty()) {
            return "데이터 없음";
        }
        
        List<TrendChangePoint> trendChanges = extractTrendChanges(dailyEquity);
        ConsistencyAnalysis consistency = analyzeConsistency(dailyEquity);
        
        StringBuilder result = new StringBuilder();
        
        BacktestDetailResponse.DailyEquityData firstData = dailyEquity.get(0);
        BacktestDetailResponse.DailyEquityData lastData = dailyEquity.get(dailyEquity.size() - 1);
        
        // 포트폴리오 총액만 사용하여 수익률 계산 (중복 계산 방지)
        double firstTotal = getPortfolioTotal(firstData);
        double lastTotal = getPortfolioTotal(lastData);
        double totalReturn = ((lastTotal - firstTotal) / firstTotal) * 100;
        
        result.append(String.format("시작일: %s, 포트폴리오 값: %,.0f원\n", firstData.date(), firstTotal));
        result.append(String.format("종료일: %s, 포트폴리오 값: %,.0f원\n", lastData.date(), lastTotal));
        result.append(String.format("전체 수익률: %.2f%% (%d일간)\n\n", totalReturn, dailyEquity.size()));
        
        // 자산 배분 분석 추가
        result.append(formatAssetAllocationAnalysis(dailyEquity));
        
        result.append(formatConsistencyAnalysis(consistency));
        
        if (trendChanges.isEmpty()) {
            result.append("\n뚜렷한 트렌드 전환이 감지되지 않음\n");
        } else {
            result.append("\n=== 주요 트렌드 변화점 ===\n");
            for (TrendChangePoint changePoint : trendChanges) {
                result.append(String.format("%s → %s 구간 시작\n", 
                        changePoint.startDate, changePoint.trendDirection));
                result.append(String.format("  초기값: %,.0f원 → 종료값: %,.0f원 (%.2f%% 변동)\n",
                        changePoint.initialValue, changePoint.currentValue, 
                        changePoint.totalReturn * 100));
                result.append(String.format("  구간 지속: %d일간\n\n", changePoint.continuationDays));
            }
        }
        
        // 개별 종목 시계열 분석 추가
        result.append(formatIndividualStockAnalysis(dailyEquity));
        
        return result.toString();
    }

    /**
     * 포트폴리오 총액 추출 (중복 계산 방지)
     */
    private double getPortfolioTotal(BacktestDetailResponse.DailyEquityData data) {
        Double portfolioTotal = data.stocks().get("포트폴리오 총액");
        if (portfolioTotal != null) {
            return portfolioTotal;
        }
        // 폴백: 모든 값의 합계
        return data.stocks().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * 자산 배분 분석
     */
    private String formatAssetAllocationAnalysis(List<BacktestDetailResponse.DailyEquityData> dailyEquity) {
        StringBuilder result = new StringBuilder();
        result.append("=== 자산 배분 분석 ===\n");
        
        BacktestDetailResponse.DailyEquityData firstData = dailyEquity.get(0);
        BacktestDetailResponse.DailyEquityData lastData = dailyEquity.get(dailyEquity.size() - 1);
        
        // 시작일 자산 배분
        Double firstStockValue = firstData.stocks().get("주식 평가액");
        Double firstCashBalance = firstData.stocks().get("현금 잔고");
        Double firstTotal = getPortfolioTotal(firstData);
        
        // 종료일 자산 배분
        Double lastStockValue = lastData.stocks().get("주식 평가액");
        Double lastCashBalance = lastData.stocks().get("현금 잔고");
        Double lastTotal = getPortfolioTotal(lastData);
        
        if (firstStockValue != null && firstCashBalance != null && firstTotal > 0) {
            double firstStockRatio = (firstStockValue / firstTotal) * 100;
            double firstCashRatio = (firstCashBalance / firstTotal) * 100;
            
            result.append(String.format("시작일 자산 구성: 주식 %.1f%% (%s원), 현금 %.1f%% (%s원)\n", 
                    firstStockRatio, formatCurrency(firstStockValue), 
                    firstCashRatio, formatCurrency(firstCashBalance)));
        }
        
        if (lastStockValue != null && lastCashBalance != null && lastTotal > 0) {
            double lastStockRatio = (lastStockValue / lastTotal) * 100;
            double lastCashRatio = (lastCashBalance / lastTotal) * 100;
            
            result.append(String.format("종료일 자산 구성: 주식 %.1f%% (%s원), 현금 %.1f%% (%s원)\n", 
                    lastStockRatio, formatCurrency(lastStockValue), 
                    lastCashRatio, formatCurrency(lastCashBalance)));
            
            // 자산 배분 변화 분석
            if (firstStockValue != null && firstCashBalance != null) {
                double stockChangeRatio = ((lastStockValue - firstStockValue) / firstStockValue) * 100;
                double cashChangeRatio = ((lastCashBalance - firstCashBalance) / firstCashBalance) * 100;
                
                result.append(String.format("자산별 변화: 주식 %.2f%%, 현금 %.2f%%\n", stockChangeRatio, cashChangeRatio));
            }
        }
        
        // 현금 관리 효율성 분석
        result.append(formatCashManagementAnalysis(dailyEquity));
        
        result.append("\n");
        return result.toString();
    }

    /**
     * 현금 관리 분석
     */
    private String formatCashManagementAnalysis(List<BacktestDetailResponse.DailyEquityData> dailyEquity) {
        List<Double> cashBalances = dailyEquity.stream()
                .map(data -> data.stocks().get("현금 잔고"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        if (cashBalances.isEmpty()) {
            return "현금 관리 데이터 없음\n";
        }
        
        double minCash = cashBalances.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxCash = cashBalances.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double avgCash = cashBalances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        StringBuilder result = new StringBuilder();
        result.append(String.format("현금 관리: 최소 %s원, 최대 %s원, 평균 %s원\n", 
                formatCurrency(minCash), formatCurrency(maxCash), formatCurrency(avgCash)));
        
        // 현금 비율의 일관성 분석
        List<Double> cashRatios = dailyEquity.stream()
                .filter(data -> {
                    Double portfolioTotal = getPortfolioTotal(data);
                    Double cashBalance = data.stocks().get("현금 잔고");
                    return portfolioTotal > 0 && cashBalance != null;
                })
                .map(data -> {
                    Double portfolioTotal = getPortfolioTotal(data);
                    Double cashBalance = data.stocks().get("현금 잔고");
                    return (cashBalance / portfolioTotal) * 100;
                })
                .collect(Collectors.toList());
        
        if (!cashRatios.isEmpty()) {
            double avgCashRatio = cashRatios.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            result.append(String.format("평균 현금 비율: %.1f%%\n", avgCashRatio));
        }
        
        return result.toString();
    }

    /**
     * 통화 포맷팅 헬퍼
     */
    private String formatCurrency(Double value) {
        if (value == null) return "0원";
        return String.format("%,.0f원", value);
    }

    /**
     * 지속된 경향성 분석 (일정한 증가/감소 패턴 감지)
     */
    private ConsistencyAnalysis analyzeConsistency(List<BacktestDetailResponse.DailyEquityData> dailyEquity) {
        if (dailyEquity.size() < 2) {
            return new ConsistencyAnalysis("데이터 부족", 0.0, false);
        }
        
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < dailyEquity.size(); i++) {
            double prevTotal = getPortfolioTotal(dailyEquity.get(i-1));
            double currTotal = getPortfolioTotal(dailyEquity.get(i));
            double dailyReturn = (currTotal - prevTotal) / prevTotal;
            returns.add(dailyReturn);
        }
        
        double avgDailyReturn = returns.stream().mapToDouble(d -> d).average().orElse(0.0);
        double avgDailyReturnPercent = avgDailyReturn * 100;
        
        double mean = returns.stream().mapToDouble(d -> d).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average()
                .orElse(0.0);
        double volatility = Math.sqrt(variance);
        
        boolean isConsistent = volatility < 0.02 && Math.abs(avgDailyReturn) > 0.005;
        
        String patternType;
        if (isConsistent) {
            if (avgDailyReturn > 0) {
                patternType = String.format("지속적 상승 패턴 (평균 %.3f%%/일)", avgDailyReturnPercent);
            } else {
                patternType = String.format("지속적 하락 패턴 (평균 %.3f%%/일)", avgDailyReturnPercent);
            }
        } else if (volatility < 0.005) {
            patternType = "횡보 성향 (변화 거의 없음)";
        } else {
            patternType = String.format("요동성 패턴 (평균 %.3f%%/일, 변동성 %.3f%%)", 
                    avgDailyReturnPercent, volatility * 100);
        }
        
        return new ConsistencyAnalysis(patternType, avgDailyReturn, isConsistent);
    }
    
    /**
     * 지속성 분석 결과 포맷팅
     */
    private String formatConsistencyAnalysis(ConsistencyAnalysis analysis) {
        StringBuilder result = new StringBuilder();
        result.append("=== 경향성 분석 ===\n");
        result.append(String.format("패턴 유형: %s\n", analysis.patternType));
        result.append(String.format("평균 일일 변화율: %.3f%%\n", analysis.avgDailyReturn * 100));
        if (analysis.isConsistent) {
            result.append("※ 지속적 트렌드 확인됨 - 일정한 방향성을 유지\n");
        }
        return result.toString();
    }

    /**
     * 트렌드 변화점 추출 로직
     */
    private List<TrendChangePoint> extractTrendChanges(List<BacktestDetailResponse.DailyEquityData> dailyEquity) {
        List<TrendChangePoint> changes = new ArrayList<>();
        
        double previousTotal = 0;
        String currentTrend = null;
        int trendCount = 0;
        String trendStartDate = "";
        double startValue = 0;
        
        for (int i = 0; i < dailyEquity.size(); i++) {
            BacktestDetailResponse.DailyEquityData data = dailyEquity.get(i);
            double currentTotal = getPortfolioTotal(data);
            
            if (i == 0) {
                previousTotal = currentTotal;
                continue;
            }
            
            double dailyReturn = (currentTotal - previousTotal) / previousTotal;
            String trend = null;
            
            if (Math.abs(dailyReturn) > 0.01) {
                trend = dailyReturn > 0 ? "상승" : "하락";
            }
            
            if (trend != null && !Objects.equals(currentTrend, trend)) {
                
                if (currentTrend != null) {
                    changes.add(new TrendChangePoint(
                            data.date(), trend, trendCount,
                            currentTotal, trendStartDate, startValue,
                            (currentTotal - startValue) / startValue
                    ));
                }
                
                currentTrend = trend;
                trendStartDate = data.date();
                startValue = currentTotal;
                trendCount = 0;
            }
            
            previousTotal = currentTotal;
            trendCount++;
        }
        
        return changes;
    }

    /**
     * ExecutionLog 포맷팅
     */
    private String formatExecutionLogs(List<ExecutionLog> executionLogs) {
        if (executionLogs.isEmpty()) {
            return "거래 기록 없음";
        }
        
        StringBuilder result = new StringBuilder();
        
        // 기본 거래 로그
        String basicLogs = executionLogs.stream()
                .map(log -> {
                    StringBuilder logEntry = new StringBuilder();
                    logEntry.append(String.format("%s | %s | %s | %s", 
                        log.getLogDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        convertActionTypeToKorean(log.getActionType()),
                        log.getCategory(),
                        log.getReason()));
                    
                    // 현금 생성 정보 추가
                    if (log.getCashGenerated() != null && log.getCashGenerated() > 0) {
                        logEntry.append(String.format(" | 현금 생성: %s", formatCurrency(log.getCashGenerated())));
                    }
                    
                    return logEntry.toString();
                })
                .collect(Collectors.joining("\n"));
        
        result.append(basicLogs);
        result.append("\n\n");
        
        // 매도 활동 분석
        result.append(formatSellingActivityAnalysis(executionLogs));
        
        // 현금 관리 분석
        result.append(formatCashFlowAnalysis(executionLogs));
        
        return result.toString();
    }

    /**
     * 매도 활동 분석
     */
    private String formatSellingActivityAnalysis(List<ExecutionLog> executionLogs) {
        StringBuilder result = new StringBuilder();
        result.append("=== 매도 활동 분석 ===\n");
        
        // 매도 관련 로그 필터링 (SELL, STOP_LOSS, TAKE_PROFIT)
        List<ExecutionLog> sellingLogs = executionLogs.stream()
                .filter(log -> log.getActionType() == ActionType.SELL || 
                              log.getActionType() == ActionType.STOP_LOSS || 
                              log.getActionType() == ActionType.TAKE_PROFIT)
                .collect(Collectors.toList());
        
        if (sellingLogs.isEmpty()) {
            result.append("매도 활동 없음\n");
        } else {
            result.append(String.format("총 매도 횟수: %d회\n", sellingLogs.size()));
            
            // 매도 타입별 분석
            Map<ActionType, Long> sellingTypeCount = sellingLogs.stream()
                    .collect(Collectors.groupingBy(ExecutionLog::getActionType, Collectors.counting()));
            
            sellingTypeCount.forEach((actionType, count) -> {
                String actionName = convertActionTypeToKorean(actionType);
                result.append(String.format("- %s: %d회\n", actionName, count));
            });
            
            // 총 현금 생성량 계산
            double totalCashGenerated = sellingLogs.stream()
                    .filter(log -> log.getCashGenerated() != null)
                    .mapToDouble(ExecutionLog::getCashGenerated)
                    .sum();
            
            if (totalCashGenerated > 0) {
                result.append(String.format("매도를 통한 총 현금 생성: %s\n", formatCurrency(totalCashGenerated)));
                
                // 평균 매도 금액
                double avgCashPerSell = totalCashGenerated / sellingLogs.size();
                result.append(String.format("평균 매도 금액: %s\n", formatCurrency(avgCashPerSell)));
            }
        }
        
        result.append("\n");
        return result.toString();
    }

    /**
     * 현금 흐름 분석
     */
    private String formatCashFlowAnalysis(List<ExecutionLog> executionLogs) {
        StringBuilder result = new StringBuilder();
        result.append("=== 현금 흐름 분석 ===\n");
        
        // 현금 생성이 있었던 로그들
        List<ExecutionLog> cashGeneratingLogs = executionLogs.stream()
                .filter(log -> log.getCashGenerated() != null && log.getCashGenerated() > 0)
                .collect(Collectors.toList());
        
        if (cashGeneratingLogs.isEmpty()) {
            result.append("현금 생성 활동 없음\n");
        } else {
            double totalCashGenerated = cashGeneratingLogs.stream()
                    .mapToDouble(ExecutionLog::getCashGenerated)
                    .sum();
            
            result.append(String.format("총 현금 생성: %s (총 %d회 활동)\n", 
                    formatCurrency(totalCashGenerated), cashGeneratingLogs.size()));
            
            // 최대 현금 생성 활동
            ExecutionLog maxCashLog = cashGeneratingLogs.stream()
                    .max(Comparator.comparing(ExecutionLog::getCashGenerated))
                    .orElse(null);
            
            if (maxCashLog != null) {
                result.append(String.format("최대 현금 생성: %s (%s, %s)\n", 
                        formatCurrency(maxCashLog.getCashGenerated()),
                        maxCashLog.getLogDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        convertActionTypeToKorean(maxCashLog.getActionType())));
            }
            
            // 월별 현금 생성 분석
            Map<String, Double> monthlyCashFlow = cashGeneratingLogs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getLogDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                            Collectors.summingDouble(ExecutionLog::getCashGenerated)
                    ));
            
            if (!monthlyCashFlow.isEmpty()) {
                result.append("\n월별 현금 생성 현황:\n");
                monthlyCashFlow.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> 
                                result.append(String.format("- %s: %s\n", entry.getKey(), formatCurrency(entry.getValue()))));
            }
        }
        
        result.append("\n");
        return result.toString();
    }
    
    /**
     * ActionType을 한국어로 변환
     */
    private String convertActionTypeToKorean(ActionType actionType) {
        return switch (actionType) {
            case SELL -> "손절";
            case BUY -> "매수";
            case STOP_LOSS -> "손절";
            case TAKE_PROFIT -> "익절";
            case REBALANCE -> "리밸런싱";
            case LIQUIDATION -> "청산";
            default -> actionType.name();
        };
    }
    
    /**
     * PortfolioSnapshot에 레포트 저장
     */
    private void saveReportToSnapshot(Long backtestId, String reportContent) {
        try {
            PortfolioSnapshot latestSnapshot = snapshotRepository.findLatestPortfolioSnapshotByBacktestId(backtestId);
            if (latestSnapshot == null) {
                log.error("PortfolioSnapshot not found for backtestId: {}", backtestId);
                return;
            }
            
            // 리포트 내용을 JSON 형태로 검증하고 필요시 포장
            String validatedReportContent = validateAndFormatReportContent(reportContent);
            
            PortfolioSnapshot updatedSnapshot = new PortfolioSnapshot(
                latestSnapshot.id(),
                latestSnapshot.backtestId(),
                latestSnapshot.baseValue(),
                latestSnapshot.currentValue(),
                latestSnapshot.createdAt(),
                latestSnapshot.metrics(),
                latestSnapshot.startAt(),
                latestSnapshot.endAt(),
                latestSnapshot.executionTime(),
                validatedReportContent,
                LocalDateTime.now()
            );
            
            snapshotRepository.savePortfolioSnapshot(updatedSnapshot);
            
            log.info("Successfully saved report to PortfolioSnapshot for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("Failed to save report to PortfolioSnapshot for backtestId: {}", backtestId, e);
            throw e;
        }
    }

    /**
     * 리포트 내용이 유효한 JSON인지 검증하고, 마크다운 코드 블록에서 JSON을 추출
     */
    private String validateAndFormatReportContent(String reportContent) {
        if (reportContent == null || reportContent.trim().isEmpty()) {
            return "{}";
        }
        
        String cleanedContent = reportContent.trim();
        
        // 1. 이미 유효한 JSON인지 확인
        try {
            objectMapper.readTree(cleanedContent);
            return cleanedContent; // 유효한 JSON이면 그대로 반환
        } catch (Exception e) {
            // JSON 파싱 실패 - 마크다운 코드 블록에서 JSON 추출 시도
        }
        
        // 2. {"content": "```json\n{...}\n```"} 형태에서 내부 JSON 추출
        try {
            JsonNode wrapperNode = objectMapper.readTree(cleanedContent);
            if (wrapperNode.has("content")) {
                String contentValue = wrapperNode.get("content").asText();
                String extractedJson = extractJsonFromMarkdown(contentValue);
                if (extractedJson != null) {
                    // 추출된 JSON이 유효한지 확인
                    objectMapper.readTree(extractedJson);
                    return extractedJson;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse as wrapper JSON, trying direct markdown extraction");
        }
        
        // 3. 직접 마크다운 코드 블록에서 JSON 추출
        String extractedJson = extractJsonFromMarkdown(cleanedContent);
        if (extractedJson != null) {
            try {
                objectMapper.readTree(extractedJson);
                return extractedJson;
            } catch (Exception e) {
                log.warn("Extracted content is not valid JSON: {}", extractedJson);
            }
        }
        
        // 4. 최후의 수단: 텍스트를 JSON으로 포장
        try {
            Object reportWrapper = Map.of("content", cleanedContent);
            return objectMapper.writeValueAsString(reportWrapper);
        } catch (Exception wrapperException) {
            log.error("Failed to wrap report content as JSON", wrapperException);
            return "{\"content\":\"Report generation failed\"}";
        }
    }

    /**
     * 마크다운 코드 블록에서 JSON 추출
     */
    private String extractJsonFromMarkdown(String content) {
        if (content == null) {
            return null;
        }
        
        // ```json ... ``` 형태 찾기
        String[] lines = content.split("\n");
        boolean inCodeBlock = false;
        boolean isJsonBlock = false;
        StringBuilder jsonContent = new StringBuilder();
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            if (trimmedLine.startsWith("```json")) {
                inCodeBlock = true;
                isJsonBlock = true;
                continue;
            } else if (trimmedLine.startsWith("```") && inCodeBlock) {
                // 코드 블록 끝
                break;
            }
            
            if (inCodeBlock && isJsonBlock) {
                if (jsonContent.length() > 0) {
                    jsonContent.append("\n");
                }
                jsonContent.append(line);
            }
        }
        
        String result = jsonContent.toString().trim();
        return result.isEmpty() ? null : result;
    }
    
    /**
     * 트렌드 변화점 데이터를 담는 클래스
     */
    private static class TrendChangePoint {
        String trendDirection;
        int continuationDays;
        double currentValue;
        String startDate;
        double initialValue;
        double totalReturn;
        
        TrendChangePoint(String changeDate, String trendDirection, int continuationDays,
                        double currentValue, String startDate, 
                        double initialValue, double totalReturn) {
            this.trendDirection = trendDirection;
            this.continuationDays = continuationDays;
            this.currentValue = currentValue;
            this.startDate = startDate;
            this.initialValue = initialValue;
            this.totalReturn = totalReturn;
        }
    }
    
    
    /**
     * 개별 종목 시계열 분석 포맷팅
     */
    private String formatIndividualStockAnalysis(List<BacktestDetailResponse.DailyEquityData> dailyEquity) {
        if (dailyEquity.isEmpty()) {
            return "\n=== 개별 종목 시계열 분석 ===\n데이터 없음\n";
        }
        
        // 개별 종목 데이터만 추출 (포트폴리오 레벨 데이터 제외)
        Set<String> individualStocks = dailyEquity.stream()
                .flatMap(data -> data.stocks().keySet().stream())
                .filter(stockName -> !stockName.equals("포트폴리오 총액") && 
                                   !stockName.equals("주식 평가액") && 
                                   !stockName.equals("현금 잔고"))
                .collect(Collectors.toSet());
        
        if (individualStocks.isEmpty()) {
            return "\n=== 개별 종목 시계열 분석 ===\n개별 종목 데이터 없음\n";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("\n=== 개별 종목 시계열 분석 ===\n");
        
        // 각 종목별 수익률 계산
        for (String stockName : individualStocks) {
            List<Double> stockValues = dailyEquity.stream()
                    .map(data -> data.stocks().get(stockName))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            if (stockValues.size() >= 2) {
                double firstValue = stockValues.get(0);
                double lastValue = stockValues.get(stockValues.size() - 1);
                double stockReturn = ((lastValue - firstValue) / firstValue) * 100;
                
                result.append(String.format("%s: %.2f%% 수익률 (%,.0f원 → %,.0f원)\n", 
                        stockName, stockReturn, firstValue, lastValue));
            }
        }
        
        return result.toString();
    }
    
    /**
     * 지속성 분석 결과를 담는 클래스
     */
    private static class ConsistencyAnalysis {
        String patternType;
        double avgDailyReturn;
        boolean isConsistent;
        
        ConsistencyAnalysis(String patternType, double avgDailyReturn, boolean isConsistent) {
            this.patternType = patternType;
            this.avgDailyReturn = avgDailyReturn;
            this.isConsistent = isConsistent;
        }
    }
}