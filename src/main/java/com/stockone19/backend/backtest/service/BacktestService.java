package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.domain.BacktestMetricsDocument;
import com.stockone19.backend.backtest.dto.*;
import com.stockone19.backend.backtest.repository.BacktestMetricsRepository;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import com.stockone19.backend.backtest.repository.BacktestRuleRepository;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.common.service.BacktestJobMappingService;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.backtest.domain.HoldingSnapshot;
import com.stockone19.backend.backtest.domain.PortfolioSnapshot;
import com.stockone19.backend.backtest.repository.SnapshotRepository;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockone19.backend.backtest.event.BacktestFailureEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@Transactional(readOnly = true)
public class BacktestService {

    private final BacktestRepository backtestRepository;
    private final BacktestJobMappingService jobMappingService;
    private final PortfolioRepository portfolioRepository;
    private final SnapshotRepository snapshotRepository;
    private final BacktestRuleRepository backtestRuleRepository;
    private final BacktestMetricsRepository backtestMetricsRepository;
    private final StockRepository stockRepository;
    private final WebClient backtestEngineWebClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    
    @Value("${backtest.callback.base-url}")
    private String callbackBaseUrl;

    public BacktestService(
            BacktestRepository backtestRepository,
            BacktestJobMappingService jobMappingService,
            PortfolioRepository portfolioRepository,
            SnapshotRepository snapshotRepository,
            BacktestRuleRepository backtestRuleRepository,
            BacktestMetricsRepository backtestMetricsRepository,
            StockRepository stockRepository,
            @Qualifier("backtestEngineWebClient") WebClient backtestEngineWebClient,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        
        this.backtestRepository = backtestRepository;
        this.jobMappingService = jobMappingService;
        this.portfolioRepository = portfolioRepository;
        this.snapshotRepository = snapshotRepository;
        this.backtestRuleRepository = backtestRuleRepository;
        this.backtestMetricsRepository = backtestMetricsRepository;
        this.stockRepository = stockRepository;
        this.backtestEngineWebClient = backtestEngineWebClient;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

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

        // 백테스트 생성 및 저장
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
     * 백테스트 상태 업데이트 (동기)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateBacktestStatus(Long backtestId, BacktestStatus status) {
        log.info("Updating backtest status to {} for backtestId: {}", status, backtestId);
        
        Backtest backtest = findBacktestById(backtestId);
        log.info("Found backtest: id={}, currentStatus={}", backtest.getId(), backtest.getStatus());
        
        backtest.updateStatus(status);
        log.info("Updated backtest status in memory to: {}", status);
        
        Backtest savedBacktest = backtestRepository.save(backtest);
        log.info("Saved backtest to DB: id={}, status={}", savedBacktest.getId(), savedBacktest.getStatus());
        
        log.info("Successfully updated backtest status to {} for backtestId: {}", status, backtestId);
    }

    /**
     * 백테스트 실패 이벤트 처리
     */
    @EventListener
    @Transactional
    public void handleBacktestFailure(BacktestFailureEvent event) {
        log.info("Handling backtest failure event for backtestId: {}", event.backtestId());
        updateBacktestStatus(event.backtestId(), BacktestStatus.FAILED);
    }

    /**
     * 백테스트 엔진에 비동기 요청 제출
     */
    @Async("backgroundTaskExecutor")
    public CompletableFuture<Void> submitToBacktestEngineAsync(Long backtestId) {
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("백테스트를 찾을 수 없습니다: " + backtestId));
            
            // 백테스트 요청 생성
            BacktestAsyncRequest request = createBacktestEngineRequest(backtest);
            
            // 백테스트 엔진에 요청
            BacktestStartResponse response = backtestEngineWebClient
                .post()
                .uri("/backtest/start")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(BacktestStartResponse.class)
                .block();
            
            // Redis에 매핑 저장
            jobMappingService.saveMapping(response.jobId(), backtestId);
            
            log.info("Backtest submitted to engine: backtestId={}, jobId={}", 
                    backtestId, response.jobId());
                    
        } catch (Exception e) {
            log.error("Failed to submit backtest to engine: backtestId={}", backtestId, e);
            // 이벤트 발행으로 상태 업데이트 처리
            eventPublisher.publishEvent(new BacktestFailureEvent(backtestId, e.getMessage()));
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 백테스트 성공 콜백 처리
     */
    @Async("backgroundTaskExecutor")
    public void handleBacktestSuccess(BacktestCallbackResponse callback) {
        Long backtestId = jobMappingService.getAndRemoveMapping(callback.jobId());
        
        if (backtestId == null) {
            log.warn("Received callback for unknown jobId: {}", callback.jobId());
            return;
        }
        
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest not found: " + backtestId));
            
            // 백테스트 상태를 완료로 업데이트
            backtest.updateStatus(BacktestStatus.COMPLETED);
            backtestRepository.save(backtest);
            
            // 2. 상세 데이터 저장 (분석용)
            BacktestExecutionResponse executionResponse = callback.toBacktestExecutionResponse();
            if (executionResponse != null) {
                saveDetailedBacktestResults(backtestId, executionResponse);
            } else {
                log.warn("Cannot convert callback to BacktestExecutionResponse for backtestId: {}", backtestId);
            }
            
            log.info("Backtest completed successfully: backtestId={}, jobId={}", 
                    backtestId, callback.jobId());
                    
        } catch (Exception e) {
            log.error("Failed to process backtest success: backtestId={}, jobId={}", 
                    backtestId, callback.jobId(), e);
        }
    }

    /**
     * 백테스트 실패 콜백 처리
     */
    @Async("backgroundTaskExecutor")
    public void handleBacktestFailure(BacktestCallbackResponse callback) {
        Long backtestId = jobMappingService.getAndRemoveMapping(callback.jobId());
        
        if (backtestId == null) {
            log.warn("Received callback for unknown jobId: {}", callback.jobId());
            return;
        }
        
        try {
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest not found: " + backtestId));
            
            backtest.updateStatus(BacktestStatus.FAILED);
            backtestRepository.save(backtest);
            
            log.info("Backtest failed: backtestId={}, jobId={}, error={}", 
                    backtestId, callback.jobId(), callback.errorMessage());
                    
        } catch (Exception e) {
            log.error("Failed to process backtest failure: backtestId={}, jobId={}", 
                    backtestId, callback.jobId(), e);
        }
    }

    /**
     * 백테스트 엔진 요청 생성
     */
    private BacktestAsyncRequest createBacktestEngineRequest(Backtest backtest) {
        // 포트폴리오에서 보유 종목 조회
        List<Holding> holdings = getHoldingsFromPortfolio(backtest.getPortfolioId());
        
        // 콜백 URL 생성 (백테스트 엔진이 완료 시 호출할 URL)
        String callbackUrl = callbackBaseUrl + "/backtests/callback";
        
        return BacktestAsyncRequest.of(
            backtest.getStartAt(),
            backtest.getEndAt(),
            holdings,
            callbackUrl
        );
    }

    /**
     * 포트폴리오에서 보유 종목 조회
     */
    private List<Holding> getHoldingsFromPortfolio(Long portfolioId) {
        return portfolioRepository.findHoldingsByPortfolioId(portfolioId);
    }

    /**
     * 상세 백테스트 결과 저장 (MongoDB + PostgreSQL)
     * 순서: MongoDB metrics → PostgreSQL portfolio_snapshot → PostgreSQL holding_snapshots → MongoDB 업데이트
     */
    @Transactional
    public void saveDetailedBacktestResults(Long backtestId, BacktestExecutionResponse response) {
        String metricId = null;
        Long portfolioSnapshotId = null;
        
        try {
            log.info("Saving detailed backtest results for backtestId: {}", backtestId);
            
            // 이미 변환된 BacktestExecutionResponse 사용
            if (response == null) {
                throw new IllegalStateException("BacktestExecutionResponse is null");
            }
            
            // 1단계: MongoDB에 성과 지표 저장 (metricId 필요)
            metricId = saveBacktestMetrics(response.metrics());
            log.info("✅ Step 1: Saved metrics to MongoDB with ID: {}", metricId);
            
            // 한 번만 조회한 backtest 객체 사용
            Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("백테스트를 찾을 수 없습니다: " + backtestId));
            
            // 2단계: PostgreSQL에 포트폴리오 스냅샷 저장 (portfolioSnapshotId 필요)
            portfolioSnapshotId = savePortfolioSnapshot(backtest, response.portfolioSnapshot(), metricId);
            log.info("✅ Step 2: Saved portfolio snapshot with ID: {}", portfolioSnapshotId);
            
            // 3단계: PostgreSQL에 일별 홀딩 스냅샷 저장 (portfolioSnapshotId 참조)
            saveDailyHoldingSnapshots(response.resultSummary(), portfolioSnapshotId);
            log.info("✅ Step 3: Saved {} daily holding snapshots", getTotalHoldingSnapshotCount(response.resultSummary()));
            
            // 4단계: MongoDB 메트릭에 portfolio_snapshot_id 연결
            updateMetricsWithSnapshotId(metricId, portfolioSnapshotId);
            log.info("✅ Step 4: Updated MongoDB metrics with portfolio snapshot ID: {}", portfolioSnapshotId);
            
            log.info("🎉 All detailed backtest results saved successfully for backtestId: {}", backtestId);
            
        } catch (Exception e) {
            log.error("💥 Failed to save detailed backtest results for backtestId: {}", backtestId, e);
            
            // 롤백 처리 (MongoDB 데이터 정리)
            if (metricId != null) {
                try {
                    backtestMetricsRepository.deleteById(metricId);
                    log.info("🔄 Rolled back MongoDB metrics with ID: {}", metricId);
                } catch (Exception rollbackError) {
                    log.error("❌ Failed to rollback MongoDB metrics: {}", rollbackError.getMessage());
                }
            }
            
            // PostgreSQL은 @Transactional로 자동 롤백됨
            log.info("🔄 PostgreSQL data will be rolled back automatically");
        }
    }

    /**
     * 저장될 홀딩 스냅샷 개수 계산 (로깅용)
     */
    private int getTotalHoldingSnapshotCount(List<BacktestExecutionResponse.DailyResultResponse> dailyResults) {
        return dailyResults.stream()
                .mapToInt(daily -> daily.stocks().size())
                .sum();
    }

    /**
     * MongoDB에 백테스트 성과 지표 저장
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
     * PostgreSQL에 포트폴리오 스냅샷 저장
     * backtest 객체를 직접 전달받아 DB 조회 최적화
     */
    private Long savePortfolioSnapshot(Backtest backtest,
                                     BacktestExecutionResponse.PortfolioSnapshotResponse snapshotResponse,
                                     String metricId) {
        
        PortfolioSnapshot portfolioSnapshot = PortfolioSnapshot.create(
                backtest.getPortfolioId(),        // 이미 조회된 객체에서 가져옴
                snapshotResponse.baseValue(),
                snapshotResponse.currentValue(),
                metricId,
                snapshotResponse.startAt(),
                snapshotResponse.endAt(),
                snapshotResponse.executionTime()
        );
        
        PortfolioSnapshot savedSnapshot = snapshotRepository.savePortfolioSnapshot(portfolioSnapshot);
        
        log.debug("Created portfolio snapshot: portfolioId={}, baseValue={:.2f}, currentValue={:.2f}", 
                 backtest.getPortfolioId(), snapshotResponse.baseValue(), snapshotResponse.currentValue());
        
        return savedSnapshot.id();
    }

    /**
     * PostgreSQL에 일별 홀딩 스냅샷 저장
     */
    private void saveDailyHoldingSnapshots(List<BacktestExecutionResponse.DailyResultResponse> dailyResults, 
                                         Long portfolioSnapshotId) {
        
        if (portfolioSnapshotId == null) {
            throw new IllegalArgumentException("portfolioSnapshotId cannot be null for holding snapshots");
        }
        
        int savedCount = 0;
        
        for (BacktestExecutionResponse.DailyResultResponse dailyResult : dailyResults) {
            LocalDateTime date = dailyResult.date();
            
            for (BacktestExecutionResponse.DailyStockResponse stockData : dailyResult.stocks()) {
                try {
                    // quantity는 이제 직접 제공됨 (정확한 수량!)
                    int quantity = stockData.quantity();
                    
                    // value는 quantity × closePrice로 정확히 계산
                    double value = stockData.getValue(); // quantity * closePrice (더 정확함)
                    
                    // HoldingSnapshot 생성 (기존 레코드 그대로 사용)
                    HoldingSnapshot holdingSnapshot = HoldingSnapshot.createWithDate(
                            stockData.closePrice(),        // price
                            quantity,                       // quantity (직접 제공됨!)
                            value,                         // value (계산됨)
                            stockData.portfolioWeight(),   // weight
                            portfolioSnapshotId,           // portfolio_snapshot_id (NOT NULL)
                            stockData.stockCode(),         // stock_code
                            date,                          // recorded_at
                            stockData.portfolioContribution(), // contribution
                            stockData.dailyReturn()        // daily_ratio
                    );
                    
                    // 저장
                    snapshotRepository.saveHoldingSnapshot(holdingSnapshot);
                    savedCount++;
                    
                    // 상세 로그 (처음 몇 개만)
                    if (savedCount <= 5) {
                        log.debug("Saved holding snapshot: stockCode={}, date={}, quantity={}, value={:.2f}", 
                                 stockData.stockCode(), date, quantity, value);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to save holding snapshot: stockCode={}, date={}, portfolioSnapshotId={}, error={}", 
                             stockData.stockCode(), date, portfolioSnapshotId, e.getMessage());
                    // 개별 실패는 로그만 남기고 계속 진행
                }
            }
        }
        
        log.info("Saved {} holding snapshots for portfolioSnapshotId: {}", savedCount, portfolioSnapshotId);
    }

    /**
     * MongoDB 메트릭에 portfolio_snapshot_id 업데이트
     */
    private void updateMetricsWithSnapshotId(String metricId, Long portfolioSnapshotId) {
        BacktestMetricsDocument metrics = backtestMetricsRepository.findById(metricId).orElse(null);
        if (metrics != null) {
            metrics.setPortfolioSnapshotId(portfolioSnapshotId);
            metrics.setUpdatedAt(java.time.LocalDateTime.now());
            backtestMetricsRepository.save(metrics);
            log.info("Updated MongoDB metrics with portfolio snapshot ID: {}", portfolioSnapshotId);
        }
    }
    
}
