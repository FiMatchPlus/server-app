package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.domain.BacktestMetricsDocument;
import com.stockone19.backend.backtest.dto.*;
import com.stockone19.backend.backtest.repository.BacktestMetricsRepository;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import com.stockone19.backend.backtest.repository.BacktestRuleRepository;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.backtest.domain.HoldingSnapshot;
import com.stockone19.backend.backtest.domain.PortfolioSnapshot;
import com.stockone19.backend.backtest.repository.SnapshotRepository;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BacktestService {

    private final BacktestRepository backtestRepository;
    private final PortfolioRepository portfolioRepository;
    private final SnapshotRepository snapshotRepository;
    private final BacktestRuleRepository backtestRuleRepository;
    private final BacktestMetricsRepository backtestMetricsRepository;
    private final StockRepository stockRepository;
    private final BacktestServerClient backtestServerClient;

    /**
     * 백테스트 생성
     *
     * @param portfolioId 포트폴리오 ID
     * @param request     백테스트 생성 요청
     * @return 생성된 백테스트 정보
     */
    @Transactional
    public CreateBacktestResult createBacktest(Long portfolioId, CreateBacktestRequest request) {
        log.info("Creating backtest for portfolioId: {}, title: {}", portfolioId, request.title());

        // 포트폴리오 존재 확인
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + portfolioId));

        // 백테스트 생성 및 저장 (먼저 PostgreSQL에 저장)
        Backtest backtest = Backtest.create(
                portfolioId,
                request.title(),
                request.description(),
                request.startAt(),
                request.endAt()
        );

        Backtest savedBacktest = backtestRepository.save(backtest);

        // 백테스트 규칙이 있는 경우 MongoDB에 저장하고 ruleId 업데이트
        if (request.rules() != null && hasRules(request.rules())) {
            String ruleId = saveBacktestRules(savedBacktest.getId(), request.rules());
            
            // JPA 엔티티의 ruleId 업데이트
            savedBacktest.updateRuleId(ruleId);
            savedBacktest = backtestRepository.save(savedBacktest);
        }

        return new CreateBacktestResult(
                savedBacktest.getId(),
                savedBacktest.getTitle(),
                savedBacktest.getDescription(),
                savedBacktest.getRuleId(),
                savedBacktest.getStartAt(),
                savedBacktest.getEndAt()
        );
    }

    /**
     * 포트폴리오별 백테스트 목록 조회
     *
     * @param portfolioId 포트폴리오 ID
     * @return 백테스트 목록
     */
    public List<Backtest> getBacktestsByPortfolioId(Long portfolioId) {
        log.info("Getting backtests for portfolioId: {}", portfolioId);

        // 포트폴리오 존재 확인
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + portfolioId));

        return backtestRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId);
    }

    /**
     * 포트폴리오별 백테스트 상태만 조회
     *
     * @param portfolioId 포트폴리오 ID
     * @return 백테스트 ID와 상태 맵 (백테스트 ID -> 상태)
     */
    public Map<String, String> getBacktestStatusesByPortfolioId(Long portfolioId) {
        log.info("Getting backtest statuses for portfolioId: {}", portfolioId);

        // 포트폴리오 존재 확인
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + portfolioId));

        List<Backtest> backtests = backtestRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId);
        
        return backtests.stream()
                .collect(Collectors.toMap(
                    backtest -> String.valueOf(backtest.getId()),
                    backtest -> backtest.getStatus().name()
                ));
    }

    /**
     * 백테스트 상세 정보 조회
     *
     * @param backtestId 백테스트 ID
     * @return 백테스트 상세 정보
     */
    public BacktestSummary getBacktestDetail(Long backtestId) {
        log.info("Getting backtest detail for backtestId: {}", backtestId);

        Backtest backtest = findBacktestById(backtestId);
        List<PortfolioSnapshot> snapshots = findPortfolioSnapshots(backtest.getPortfolioId(), backtestId);
        PortfolioSnapshot latestSnapshot = getLatestSnapshot(snapshots);

        String period = formatBacktestPeriod(backtest);
        Integer executionTime = extractExecutionTime(latestSnapshot);
        BacktestMetrics metrics = getBacktestMetrics(latestSnapshot);
        List<DailyReturn> dailyReturns = createDailyReturns(snapshots);

        return BacktestSummary.of(
                backtest.getId(),
                backtest.getTitle(),
                period,
                executionTime,
                backtest.getCreatedAt(),
                metrics,
                dailyReturns
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
     * 포트폴리오 스냅샷 조회
     */
    private List<PortfolioSnapshot> findPortfolioSnapshots(Long portfolioId, Long backtestId) {
        List<PortfolioSnapshot> snapshots = snapshotRepository.findPortfolioSnapshotsByPortfolioId(portfolioId);
        if (snapshots.isEmpty()) {
            throw new ResourceNotFoundException("No portfolio snapshots found for backtest id: " + backtestId);
        }
        return snapshots;
    }

    /**
     * 최신 스냅샷 조회
     */
    private PortfolioSnapshot getLatestSnapshot(List<PortfolioSnapshot> snapshots) {
        return snapshots.get(snapshots.size() - 1);
    }

    /**
     * 백테스트 기간 포맷팅
     */
    private String formatBacktestPeriod(Backtest backtest) {
        return formatPeriod(backtest.getStartAt().toLocalDate(), backtest.getEndAt().toLocalDate());
    }

    /**
     * 실행 시간 추출
     */
    private Integer extractExecutionTime(PortfolioSnapshot latestSnapshot) {
        return latestSnapshot.executionTime() != null ?
                latestSnapshot.executionTime().intValue() : null;
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

    private String formatPeriod(LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        return startDate.format(formatter) + " ~ " + endDate.format(formatter);
    }


    /**
     * 일별 수익률 데이터 생성
     */
    private List<DailyReturn> createDailyReturns(List<PortfolioSnapshot> snapshots) {
        Map<String, Stock> stockCache = new HashMap<>();

        return snapshots.stream()
                .map(snapshot -> createDailyReturn(snapshot, stockCache))
                .collect(Collectors.toList());
    }

    /**
     * 단일 스냅샷에 대한 일별 수익률 생성
     */
    private DailyReturn createDailyReturn(PortfolioSnapshot snapshot, Map<String, Stock> stockCache) {
        LocalDate date = snapshot.createdAt().toLocalDate();
        List<HoldingSnapshot> holdingSnapshots = getHoldingSnapshots(snapshot.id());
        Map<String, Double> stockReturns = calculateStockReturns(holdingSnapshots, snapshot, stockCache);

        return DailyReturn.of(date, stockReturns);
    }

    /**
     * 홀딩 스냅샷 조회
     */
    private List<HoldingSnapshot> getHoldingSnapshots(Long portfolioSnapshotId) {
        return snapshotRepository.findHoldingSnapshotsByPortfolioSnapshotId(portfolioSnapshotId);
    }

    /**
     * 종목별 수익률 계산
     */
    private Map<String, Double> calculateStockReturns(List<HoldingSnapshot> holdingSnapshots,
                                                      PortfolioSnapshot portfolioSnapshot,
                                                      Map<String, Stock> stockCache) {
        Map<String, Double> stockReturns = new HashMap<>();

        for (HoldingSnapshot holdingSnapshot : holdingSnapshots) {
            String stockName = getStockName(holdingSnapshot.stockCode(), stockCache);
            double returnRate = calculateReturnRate(holdingSnapshot, portfolioSnapshot);
            stockReturns.put(stockName, returnRate);
        }

        return stockReturns;
    }

    /**
     * 종목 이름 조회 (캐싱 적용)
     */
    private String getStockName(String stockCode, Map<String, Stock> stockCache) {
        Stock stock = stockCache.computeIfAbsent(stockCode, code ->
                stockRepository.findByTicker(code).orElse(null));
        return stock != null ? stock.getName() : stockCode;
    }

    /**
     * 홀딩 수익률 계산
     *
     * @param holdingSnapshot   홀딩 스냅샷
     * @param portfolioSnapshot 포트폴리오 스냅샷
     * @return 수익률 (%)
     */
    private double calculateReturnRate(HoldingSnapshot holdingSnapshot, PortfolioSnapshot portfolioSnapshot) {
        double baseValue = holdingSnapshot.price() * holdingSnapshot.quantity();
        if (baseValue == 0) {
            return 0.0;
        }

        double currentValue = holdingSnapshot.value();
        return ((currentValue - baseValue) / baseValue) * 100;
    }


    private boolean hasRules(CreateBacktestRequest.RulesRequest rules) {
        return (rules.stopLoss() != null && !rules.stopLoss().isEmpty()) ||
               (rules.takeProfit() != null && !rules.takeProfit().isEmpty()) ||
               (rules.memo() != null && !rules.memo().trim().isEmpty());
    }

    private String saveBacktestRules(Long backtestId, CreateBacktestRequest.RulesRequest rulesRequest) {
        List<BacktestRuleDocument.RuleItem> stopLossItems = convertToRuleItems(rulesRequest.stopLoss());
        List<BacktestRuleDocument.RuleItem> takeProfitItems = convertToRuleItems(rulesRequest.takeProfit());

        BacktestRuleDocument backtestRule = new BacktestRuleDocument(
                backtestId,
                rulesRequest.memo(),
                stopLossItems,
                takeProfitItems
        );

        BacktestRuleDocument savedRule = backtestRuleRepository.save(backtestRule);
        log.info("Saved backtest rule to MongoDB with id: {}", savedRule.getId());
        
        return savedRule.getId();
    }

    private List<BacktestRuleDocument.RuleItem> convertToRuleItems(List<CreateBacktestRequest.RuleItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        
        return items.stream()
                .map(item -> new BacktestRuleDocument.RuleItem(
                        item.category(),
                        item.threshold(),
                        item.description()
                ))
                .toList();
    }

    /**
     * 백테스트 실행 (WebFlux)
     */
    @Transactional
    public Mono<BacktestExecutionResponse> executeBacktestReactive(Long backtestId) {
        log.info("Starting reactive backtest execution for backtestId: {}", backtestId);
        
        return Mono.fromCallable(() -> findBacktestById(backtestId))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(backtest -> updateBacktestStatus(backtest.getId(), BacktestStatus.RUNNING))
                .flatMap(this::prepareBacktestRequest)
                .flatMap(backtestServerClient::executeBacktest)
                .flatMap(response -> saveBacktestResultsSimple(backtestId, response))
                .doOnSuccess(response -> {
                    log.info("Backtest execution completed successfully for backtestId: {}", backtestId);
                    updateBacktestStatusAsync(backtestId, BacktestStatus.COMPLETED);
                })
                .doOnError(error -> {
                    log.error("Backtest execution failed for backtestId: {}, error: {}", backtestId, error.getMessage());
                    updateBacktestStatusAsync(backtestId, BacktestStatus.FAILED);
                });
    }

    /**
     * 백테스트 요청 데이터 준비
     */
    private Mono<BacktestExecutionRequest> prepareBacktestRequest(Backtest backtest) {
        return Mono.fromCallable(() -> {
            // 포트폴리오 존재 확인
            portfolioRepository.findById(backtest.getPortfolioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + backtest.getPortfolioId()));
            
            // 포트폴리오의 홀딩 정보 조회
            List<Holding> portfolioHoldings = portfolioRepository.findHoldingsByPortfolioId(backtest.getPortfolioId());
            
            if (portfolioHoldings.isEmpty()) {
                throw new ResourceNotFoundException("No holdings found for portfolio id: " + backtest.getPortfolioId());
            }
            
            // Holding을 BacktestExecutionRequest.HoldingRequest로 변환
            List<BacktestExecutionRequest.HoldingRequest> holdings = portfolioHoldings.stream()
                    .map(holding -> BacktestExecutionRequest.HoldingRequest.builder()
                            .code(holding.symbol())
                            .quantity(holding.shares())
                            .build())
                    .collect(Collectors.toList());
            
            log.info("Prepared backtest request with {} holdings for portfolioId: {}", 
                    holdings.size(), backtest.getPortfolioId());
            
            return BacktestExecutionRequest.builder()
                    .start(backtest.getStartAt())
                    .end(backtest.getEndAt())
                    .holdings(holdings)
                    .rebalanceFrequency("daily")
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 백테스트 결과 저장
     */
    private Mono<BacktestExecutionResponse> saveBacktestResultsSimple(Long backtestId, BacktestExecutionResponse response) {
        return Mono.fromCallable(() -> {
            log.info("Saving backtest results for backtestId: {}", backtestId);
            
            String metricId = null;
            try {
                // 1단계: BacktestMetrics를 MongoDB에 저장
                metricId = saveBacktestMetrics(response.metrics());
                log.info("Saved metrics to MongoDB with ID: {}", metricId);
                
                // 2단계: PostgreSQL 트랜잭션으로 PortfolioSnapshot과 HoldingSnapshot 저장
                Long portfolioSnapshotId = saveBacktestResultsInTransaction(backtestId, response, metricId);
                log.info("Saved portfolio snapshot with ID: {}", portfolioSnapshotId);
                
                // 3단계: MongoDB 메트릭에 portfolio_snapshot_id 업데이트
                updateMetricsWithSnapshotId(metricId, portfolioSnapshotId);
                
                log.info("All backtest results saved successfully for backtestId: {}", backtestId);
                return response;
                
            } catch (Exception e) {
                log.error("Failed to save backtest results for backtestId: {}, error: {}", backtestId, e.getMessage());
                
                // MongoDB 롤백 (메트릭 삭제)
                if (metricId != null) {
                    try {
                        backtestMetricsRepository.deleteById(metricId);
                        log.info("Rolled back MongoDB metrics with ID: {}", metricId);
                    } catch (Exception rollbackError) {
                        log.error("Failed to rollback MongoDB metrics: {}", rollbackError.getMessage());
                    }
                }
                
                throw new RuntimeException("Failed to save backtest results: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }


    /**
     * PostgreSQL 트랜잭션으로 PortfolioSnapshot과 HoldingSnapshot 저장
     */
    private Long saveBacktestResultsInTransaction(Long backtestId, BacktestExecutionResponse response, String metricId) {
        try {
            // PortfolioSnapshot 저장
            Long portfolioSnapshotId = savePortfolioSnapshot(backtestId, response.portfolioSnapshot(), metricId);
            
            // 일별 HoldingSnapshot 저장
            saveDailyHoldingSnapshots(response.resultSummary(), portfolioSnapshotId);
            
            return portfolioSnapshotId;
        } catch (Exception e) {
            log.error("Transaction failed during PostgreSQL operations: {}", e.getMessage());
            throw e; // 트랜잭션 롤백을 위해 예외 재발생
        }
    }

    /**
     * 1단계: BacktestMetrics를 MongoDB에 저장
     */
    private String saveBacktestMetrics(BacktestExecutionResponse.BacktestMetricsResponse metricsResponse) {
        BacktestMetricsDocument metricsDoc = new BacktestMetricsDocument(
                null, // portfolioSnapshotId는 나중에 업데이트
                metricsResponse.totalReturn(),
                metricsResponse.annualizedReturn(),
                metricsResponse.volatility(),
                metricsResponse.sharpeRatio(),
                metricsResponse.maxDrawdown(),
                metricsResponse.var95(),
                metricsResponse.var99(),
                metricsResponse.cvar95(),
                metricsResponse.cvar99(),
                metricsResponse.winRate(),
                metricsResponse.profitLossRatio()
        );
        
        BacktestMetricsDocument savedMetrics = backtestMetricsRepository.save(metricsDoc);
        return savedMetrics.getId();
    }

    /**
     * 2단계: PortfolioSnapshot을 PostgreSQL에 저장
     */
    private Long savePortfolioSnapshot(Long backtestId, 
                                     BacktestExecutionResponse.PortfolioSnapshotResponse snapshotResponse,
                                     String metricId) {
        Backtest backtest = findBacktestById(backtestId);
        
        PortfolioSnapshot portfolioSnapshot = PortfolioSnapshot.create(
                backtest.getPortfolioId(),
                snapshotResponse.baseValue(),
                snapshotResponse.currentValue(),
                metricId,
                snapshotResponse.startAt(),
                snapshotResponse.endAt(),
                snapshotResponse.executionTime()
        );
        
        PortfolioSnapshot savedSnapshot = snapshotRepository.savePortfolioSnapshot(portfolioSnapshot);
        return savedSnapshot.id();
    }

    /**
     * 3단계: result_summary의 일별 데이터를 HoldingSnapshot으로 저장
     */
    private void saveDailyHoldingSnapshots(List<BacktestExecutionResponse.DailyResultResponse> dailyResults, Long portfolioSnapshotId) {
        for (BacktestExecutionResponse.DailyResultResponse dailyResult : dailyResults) {
            for (BacktestExecutionResponse.DailyStockResponse stockData : dailyResult.stocks()) {
                // quantity는 value / close_price로 계산
                int quantity = (int) (stockData.value() / stockData.closePrice());
                
                HoldingSnapshot holdingSnapshot = HoldingSnapshot.createWithDate(
                        stockData.closePrice(),
                        quantity,
                        stockData.value(),
                        stockData.portfolioWeight(),
                        portfolioSnapshotId,
                        stockData.stockCode(),
                        stockData.date(),
                        stockData.portfolioContribution(),
                        stockData.dailyReturn()
                );
                
                snapshotRepository.saveHoldingSnapshot(holdingSnapshot);
            }
        }
    }

    /**
     * 4단계: MongoDB 메트릭에 portfolio_snapshot_id 업데이트
     */
    private void updateMetricsWithSnapshotId(String metricId, Long portfolioSnapshotId) {
        BacktestMetricsDocument metrics = backtestMetricsRepository.findById(metricId).orElse(null);
        if (metrics != null) {
            metrics.setPortfolioSnapshotId(portfolioSnapshotId);
            metrics.setUpdatedAt(java.time.LocalDateTime.now());
            backtestMetricsRepository.save(metrics);
        }
    }

    /**
     * 백테스트 상태 업데이트 (동기)
     */
    private void updateBacktestStatus(Long backtestId, BacktestStatus status) {
        log.info("Updating backtest status to {} for backtestId: {}", status, backtestId);
        
        Backtest backtest = findBacktestById(backtestId);
        backtest.updateStatus(status);
        backtestRepository.save(backtest);
        
        log.debug("Successfully updated backtest status to {} for backtestId: {}", status, backtestId);
    }

    /**
     * 백테스트 상태 업데이트 (비동기)
     */
    private void updateBacktestStatusAsync(Long backtestId, BacktestStatus status) {
        Mono.fromRunnable(() -> updateBacktestStatus(backtestId, status))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
    
}
