package com.fimatchplus.backend.backtest.service;

import com.fimatchplus.backend.backtest.domain.Backtest;
import com.fimatchplus.backend.backtest.domain.HoldingSnapshot;
import com.fimatchplus.backend.backtest.domain.PortfolioSnapshot;
import com.fimatchplus.backend.backtest.domain.BenchmarkPrice;
import com.fimatchplus.backend.backtest.domain.BenchmarkIndex;
import com.fimatchplus.backend.backtest.dto.BacktestDetailResponse;
import com.fimatchplus.backend.backtest.dto.BacktestMetrics;
import com.fimatchplus.backend.backtest.dto.BacktestMetaData;
import com.fimatchplus.backend.backtest.repository.BacktestRepository;
import com.fimatchplus.backend.backtest.repository.SnapshotRepository;
import com.fimatchplus.backend.backtest.repository.BenchmarkPriceRepository;
import com.fimatchplus.backend.common.exception.ResourceNotFoundException;
import com.fimatchplus.backend.stock.domain.Stock;
import com.fimatchplus.backend.stock.repository.StockRepository;
import com.fimatchplus.backend.backtest.repository.BacktestRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BacktestQueryService {

    private final BacktestRepository backtestRepository;
    private final SnapshotRepository snapshotRepository;
    private final StockRepository stockRepository;
    private final BenchmarkPriceRepository benchmarkPriceRepository;
    private final BacktestRuleRepository backtestRuleRepository;
    private final ObjectMapper objectMapper;

    /**
     * 백테스트 메타데이터 조회
     *
     * @param backtestId 백테스트 ID
     * @return 백테스트 메타데이터 (설정 정보)
     */
    public BacktestMetaData getBacktestMetaData(Long backtestId) {
        log.info("Getting backtest metadata for backtestId: {}", backtestId);

        Backtest backtest = findBacktestById(backtestId);
        BacktestRuleDocument rule = getBacktestRuleById(backtest.getRuleId());

        return BacktestMetaData.of(
                backtest.getId(),
                backtest.getPortfolioId(),
                backtest.getTitle(),
                backtest.getDescription(),
                backtest.getStartAt(),
                backtest.getEndAt(),
                backtest.getCreatedAt(),
                backtest.getBenchmarkCode(),
                backtest.getStatus(),
                rule
        );
    }

    /**
     * 백테스트 결과 상세 정보 조회 (새로운 응답 구조)
     *
     * @param backtestId 백테스트 ID
     * @return 백테스트 상세 정보
     */
    public BacktestDetailResponse getBacktestDetail(Long backtestId) {
        log.info("Getting backtest detail for backtestId: {}", backtestId);

        Backtest backtest = findBacktestById(backtestId);
        PortfolioSnapshot latestSnapshot = snapshotRepository.findLatestPortfolioSnapshotByBacktestId(backtestId);
        
        String period = formatBacktestPeriod(backtest);
        Double executionTime = latestSnapshot.executionTime();
        BacktestMetrics metrics = getBacktestMetrics(latestSnapshot);
        
        List<HoldingSnapshot> allHoldingSnapshots = snapshotRepository.findHoldingSnapshotsByBacktestId(backtestId);
        
        Map<String, String> stockCodeToNameMap = getStockCodeToNameMap(allHoldingSnapshots);
        
        List<BacktestDetailResponse.DailyEquityData> dailyEquity = createDailyEquityDataOptimized(allHoldingSnapshots, stockCodeToNameMap);
        
        String benchmarkCode = backtest.getBenchmarkCode();
        String benchmarkName = getBenchmarkName(benchmarkCode);
        List<BacktestDetailResponse.BenchmarkData> benchmarkData = getBenchmarkData(benchmarkCode, backtest.getStartAt(), backtest.getEndAt());
        
        List<HoldingSnapshot> latestHoldingSnapshots = allHoldingSnapshots.stream()
                .filter(holding -> holding.portfolioSnapshotId().equals(latestSnapshot.id()))
                .collect(Collectors.toList());
        List<BacktestDetailResponse.HoldingData> holdings = createHoldingDataOptimized(latestHoldingSnapshots, stockCodeToNameMap);

        BacktestRuleDocument rules = getBacktestRuleById(backtest.getRuleId());

        // 리포트 내용을 JSON으로 파싱
        JsonNode reportJson = parseReportContent(latestSnapshot.reportContent());

        return BacktestDetailResponse.of(
                latestSnapshot.id().toString(),
                backtest.getTitle(),
                period,
                executionTime,
                benchmarkCode,
                benchmarkName,
                metrics,
                dailyEquity,
                benchmarkData,
                holdings,
                reportJson,
                rules
        );
    }

    /**
     * 백테스트 정보 조회
     */
    private Backtest findBacktestById(Long backtestId) {
        return backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest not found with id: " + backtestId));
    }

    /**
     * 백테스트 Rule 정보 조회 (test 컬렉션에서 조회)
     * rule_id가 null이거나 존재하지 않는 경우 null 반환
     */
    private BacktestRuleDocument getBacktestRuleById(String ruleId) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            log.debug("Backtest Rule ID is null or empty, returning null");
            return null;
        }

        try {
            return backtestRuleRepository.findById(ruleId).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to fetch backtest rule with id: {}, returning null", ruleId, e);
            return null;
        }
    }

    /**
     * 백테스트 기간 포맷팅
     */
    private String formatBacktestPeriod(Backtest backtest) {
        return formatPeriod(backtest.getStartAt().toLocalDate(), backtest.getEndAt().toLocalDate());
    }

    /**
     * 백테스트 성과 지표 조회 (JSON에서 파싱)
     */
    private BacktestMetrics getBacktestMetrics(PortfolioSnapshot latestSnapshot) {
        if (latestSnapshot.metrics() == null || latestSnapshot.metrics().trim().isEmpty()) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> metricsMap = objectMapper.readValue(latestSnapshot.metrics(), Map.class);
            
            return BacktestMetrics.of(
                    getDoubleValue(metricsMap, "totalReturn"),
                    getDoubleValue(metricsMap, "annualizedReturn"),
                    getDoubleValue(metricsMap, "volatility"),
                    getDoubleValue(metricsMap, "sharpeRatio"),
                    getDoubleValue(metricsMap, "maxDrawdown"),
                    getDoubleValue(metricsMap, "winRate"),
                    getDoubleValue(metricsMap, "profitLossRatio")
            );
        } catch (Exception e) {
            log.error("Failed to parse metrics JSON for portfolioSnapshotId: {}", latestSnapshot.id(), e);
            return null;
        }
    }
    
    /**
     * JSON에서 Double 값 추출 (안전한 타입 변환)
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * 기간 포맷팅
     */
    private String formatPeriod(LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        return startDate.format(formatter) + " ~ " + endDate.format(formatter);
    }


    /**
     * 주식 코드에서 주식명으로의 매핑을 한 번에 생성 (N+1 문제 해결)
     */
    private Map<String, String> getStockCodeToNameMap(List<HoldingSnapshot> holdingSnapshots) {
        Set<String> stockCodes = holdingSnapshots.stream()
                .filter(holding -> !"PORTFOLIO_DAILY".equals(holding.stockCode()))  // 포트폴리오 레벨 데이터 제외
                .map(HoldingSnapshot::stockCode)
                .collect(Collectors.toSet());
        
        if (stockCodes.isEmpty()) {
            return new HashMap<>();
        }
        
        List<Stock> stocks = stockRepository.findByTickerIn(new ArrayList<>(stockCodes));
        
        return stocks.stream()
                .collect(Collectors.toMap(
                        Stock::getTicker,
                        Stock::getName,
                        (existing, replacement) -> replacement
                ));
    }

    /**
     * 일별 평가액 데이터 생성 (최적화된 버전 - N+1 문제 해결)
     */
    private List<BacktestDetailResponse.DailyEquityData> createDailyEquityDataOptimized(
            List<HoldingSnapshot> allHoldingSnapshots,
            Map<String, String> stockCodeToNameMap) {
        
        Map<String, List<HoldingSnapshot>> holdingsByDate = allHoldingSnapshots.stream()
                .collect(Collectors.groupingBy(
                        holding -> holding.recordedAt().toLocalDate().toString()
                ));
        
        return holdingsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String date = entry.getKey();
                    List<HoldingSnapshot> holdings = entry.getValue();
                    
                    // 포트폴리오 레벨 데이터와 개별 주식 데이터를 분리
                    Optional<HoldingSnapshot> portfolioData = holdings.stream()
                            .filter(holding -> "PORTFOLIO_DAILY".equals(holding.stockCode()))
                            .findFirst();
                    
                    // 개별 주식 데이터만 필터링
                    List<HoldingSnapshot> stockHoldings = holdings.stream()
                            .filter(holding -> !"PORTFOLIO_DAILY".equals(holding.stockCode()))
                            .toList();
                    
                    Map<String, Double> stockEquities = stockHoldings.stream()
                            .collect(Collectors.groupingBy(
                                    holding -> stockCodeToNameMap.getOrDefault(holding.stockCode(), holding.stockCode()),
                                    Collectors.summingDouble(HoldingSnapshot::value)
                            ));
                    
                    // 포트폴리오 레벨 데이터가 있으면 추가 정보 포함
                    if (portfolioData.isPresent()) {
                        HoldingSnapshot portfolio = portfolioData.get();
                        stockEquities.put("포트폴리오 총액", portfolio.value());           // portfolio_value
                        stockEquities.put("주식 평가액", portfolio.contribution());        // stock_value
                        stockEquities.put("현금 잔고", portfolio.dailyRatio());           // cash_balance
                    }
                    
                    return new BacktestDetailResponse.DailyEquityData(date, stockEquities);
                })
                .collect(Collectors.toList());
    }

    /**
     * 보유 정보 데이터 생성 (최적화된 버전 - N+1 문제 해결)
     * 백테스트에서는 보유 수량이 변하지 않으므로 첫 번째 값만 사용
     */
    private List<BacktestDetailResponse.HoldingData> createHoldingDataOptimized(
            List<HoldingSnapshot> holdingSnapshots,
            Map<String, String> stockCodeToNameMap) {
        
        // PORTFOLIO_DAILY 레코드 제외하고 실제 주식 데이터만 처리
        Map<String, Integer> finalHoldings = holdingSnapshots.stream()
                .filter(holding -> !"PORTFOLIO_DAILY".equals(holding.stockCode()))
                .collect(Collectors.groupingBy(
                        holding -> stockCodeToNameMap.getOrDefault(holding.stockCode(), holding.stockCode()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.isEmpty() ? 0 : list.get(0).quantity()
                        )
                ));
        
        return finalHoldings.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new BacktestDetailResponse.HoldingData(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 벤치마크 이름 조회
     */
    private String getBenchmarkName(String benchmarkCode) {
        return BenchmarkIndex.getNameByCode(benchmarkCode);
    }

    /**
     * 벤치마크 일별 데이터 조회
     */
    private List<BacktestDetailResponse.BenchmarkData> getBenchmarkData(String benchmarkCode, LocalDateTime startAt, LocalDateTime endAt) {
        if (benchmarkCode == null || benchmarkCode.trim().isEmpty()) {
            return List.of();
        }

        try {
            List<BenchmarkPrice> benchmarkPrices = benchmarkPriceRepository.findByIndexCodeAndDateRange(
                    benchmarkCode, startAt, endAt);

            return benchmarkPrices.stream()
                    .map(price -> new BacktestDetailResponse.BenchmarkData(
                            price.datetime().toLocalDate().toString(),
                            price.closePrice().doubleValue(),
                            price.changeRate().doubleValue()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch benchmark data for code: {}", benchmarkCode, e);
            return List.of();
        }
    }

    /**
     * 리포트 내용을 JSON으로 파싱
     */
    private JsonNode parseReportContent(String reportContent) {
        if (reportContent == null || reportContent.trim().isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readTree(reportContent);
        } catch (Exception e) {
            log.warn("Failed to parse report content as JSON, returning null. Content: {}", reportContent, e);
            return null;
        }
    }
}
