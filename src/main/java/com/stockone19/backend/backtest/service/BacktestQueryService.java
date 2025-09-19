package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.domain.BacktestMetricsDocument;
import com.stockone19.backend.backtest.domain.HoldingSnapshot;
import com.stockone19.backend.backtest.domain.PortfolioSnapshot;
import com.stockone19.backend.backtest.dto.BacktestDetailResponse;
import com.stockone19.backend.backtest.dto.BacktestMetrics;
import com.stockone19.backend.backtest.repository.BacktestMetricsRepository;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import com.stockone19.backend.backtest.repository.SnapshotRepository;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BacktestQueryService {

    private final BacktestRepository backtestRepository;
    private final SnapshotRepository snapshotRepository;
    private final BacktestMetricsRepository backtestMetricsRepository;
    private final StockRepository stockRepository;

    /**
     * 백테스트 상세 정보 조회 (새로운 응답 구조)
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
        
        // portfolio_snapshot_id로 홀딩 데이터 조회
        List<HoldingSnapshot> allHoldingSnapshots = snapshotRepository.findHoldingSnapshotsByPortfolioSnapshotId(latestSnapshot.id());
        
        // 주식 정보를 한 번에 조회 (N+1 문제 해결)
        Map<String, String> stockCodeToNameMap = getStockCodeToNameMap(allHoldingSnapshots);
        
        // 일별 평가액 데이터 생성 (holding_snapshots의 recorded_at 기준으로 날짜별 그룹화)
        List<BacktestDetailResponse.DailyEquityData> dailyEquity = createDailyEquityDataOptimized(allHoldingSnapshots, stockCodeToNameMap);
        
        // 최신 보유 정보 조회
        List<HoldingSnapshot> latestHoldingSnapshots = allHoldingSnapshots.stream()
                .filter(holding -> holding.portfolioSnapshotId().equals(latestSnapshot.id()))
                .collect(Collectors.toList());
        List<BacktestDetailResponse.HoldingData> holdings = createHoldingDataOptimized(latestHoldingSnapshots, stockCodeToNameMap);

        return BacktestDetailResponse.of(
                latestSnapshot.id().toString(),  // history_id
                backtest.getTitle(),             // name
                period,                          // period
                executionTime,                   // executionTime
                metrics,                         // metrics
                dailyEquity,                     // dailyEquity
                holdings                         // holdings
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
     * 백테스트 기간 포맷팅
     */
    private String formatBacktestPeriod(Backtest backtest) {
        return formatPeriod(backtest.getStartAt().toLocalDate(), backtest.getEndAt().toLocalDate());
    }

    /**
     * 백테스트 성과 지표 조회
     */
    private BacktestMetrics getBacktestMetrics(PortfolioSnapshot latestSnapshot) {
        if (latestSnapshot.metricId() == null) {
            return null;
        }

        BacktestMetricsDocument metricsDoc = backtestMetricsRepository
                .findById(latestSnapshot.metricId())
                .orElse(null);

        if (metricsDoc == null) {
            return null;
        }

        return BacktestMetrics.of(
                metricsDoc.getTotalReturn(),
                metricsDoc.getAnnualizedReturn(),
                metricsDoc.getVolatility(),
                metricsDoc.getSharpeRatio(),
                metricsDoc.getMaxDrawdown(),
                metricsDoc.getWinRate(),
                metricsDoc.getProfitLossRatio()
        );
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
                .map(HoldingSnapshot::stockCode)
                .collect(Collectors.toSet());
        
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
        
        // holding_snapshots을 recorded_at 기준으로 날짜별 그룹화
        Map<String, List<HoldingSnapshot>> holdingsByDate = allHoldingSnapshots.stream()
                .collect(Collectors.groupingBy(
                        holding -> holding.recordedAt().toLocalDate().toString()
                ));
        
        // 날짜순으로 정렬하여 반환
        return holdingsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // 날짜순 정렬
                .map(entry -> {
                    String date = entry.getKey();
                    List<HoldingSnapshot> holdings = entry.getValue();
                    
                    // 주식별 평가액 맵 생성 (주식명 -> 평가액)
                    // 같은 주식 코드가 여러 개 있을 경우 평가액을 합산
                    Map<String, Double> stockEquities = holdings.stream()
                            .collect(Collectors.groupingBy(
                                    holding -> stockCodeToNameMap.getOrDefault(holding.stockCode(), holding.stockCode()),
                                    Collectors.summingDouble(HoldingSnapshot::value)
                            ));
                    
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
        
        // 주식 코드별로 그룹화하여 첫 번째 보유 수량만 가져오기 (백테스트에서는 수량이 변하지 않음)
        Map<String, Integer> finalHoldings = holdingSnapshots.stream()
                .collect(Collectors.groupingBy(
                        holding -> stockCodeToNameMap.getOrDefault(holding.stockCode(), holding.stockCode()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.isEmpty() ? 0 : list.get(0).quantity()
                        )
                ));
        
        // 수량이 0보다 큰 보유 종목만 반환
        return finalHoldings.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new BacktestDetailResponse.HoldingData(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
