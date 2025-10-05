package com.stockone19.backend.portfolio.service;

import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.portfolio.domain.BenchmarkIndex;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.Rules;
import com.stockone19.backend.portfolio.event.PortfolioCreatedEvent;
import com.stockone19.backend.portfolio.dto.*;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import com.stockone19.backend.portfolio.repository.RulesRepository;
import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final RulesRepository rulesRepository;
    private final StockService stockService;
    private final BenchmarkDeterminerService benchmarkDeterminerService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 사용자별 포트폴리오 합계 정보 조회
     *
     * @param userId 사용자 식별자
     * @return 사용자 보유 포트폴리오 총 합계 정보 (총 자산, 일간 수익 금액, 일간 수익률)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // 트랜잭션 없이 실행
    public PortfolioSummaryResponse getPortfolioSummary(Long userId) {
        log.info("Getting portfolio summary for userId: {}", userId);

        // 1. DB 조회만 트랜잭션으로 처리 (빠르게 완료)
        List<Holding> allHoldings = getAllUserHoldingsWithTransaction(userId);
        if (allHoldings.isEmpty()) {
            return new PortfolioSummaryResponse(0.0, 0.0, 0.0);
        }

        // 2. 외부 API 호출은 트랜잭션 밖에서 처리
        try {
            Map<String, StockService.StockPriceInfo> priceMap = getPriceMapForHoldings(allHoldings);
            PortfolioSummaryTotals totals = calculatePortfolioSummaryTotals(allHoldings, priceMap);

            return new PortfolioSummaryResponse(
                    totals.totalAssets,
                    totals.totalDailyReturn,
                    totals.totalDailyChange
            );
        } catch (Exception e) {
            log.error("KIS API 호출 실패, 기존 가격 데이터 사용: {}", e.getMessage());
            // 외부 API 실패 시 기존 데이터로 폴백
            return getPortfolioSummaryFromStoredData(allHoldings);
        }
    }

    @Transactional(readOnly = true, timeout = 5)  // DB 조회만 빠르게 처리
    public List<Holding> getAllUserHoldingsWithTransaction(Long userId) {
        return portfolioRepository.findHoldingsByUserId(userId);
    }

    private PortfolioSummaryResponse getPortfolioSummaryFromStoredData(List<Holding> allHoldings) {
        double totalAssets = allHoldings.stream()
                .mapToDouble(Holding::totalValue)
                .sum();
        
        return new PortfolioSummaryResponse(totalAssets, 0.0, 0.0);
    }


    private Map<String, StockService.StockPriceInfo> getPriceMapForHoldings(List<Holding> holdings) {
        List<String> tickers = holdings.stream()
                .map(Holding::symbol)
                .distinct()
                .collect(Collectors.toList());

        return stockService.getMultiCurrentPrices(tickers);
    }

    private PortfolioSummaryTotals calculatePortfolioSummaryTotals(List<Holding> holdings, Map<String, StockService.StockPriceInfo> priceMap) {
        double totalAssets = 0.0;
        double totalDailyChange = 0.0;

        for (Holding holding : holdings) {
            StockService.StockPriceInfo priceInfo = priceMap.get(holding.symbol());
            
            if (priceInfo != null) {
                double currentPrice = priceInfo.currentPrice();
                double currentValue = currentPrice * holding.shares();
                double dailyChange = priceInfo.dailyChangePrice() * holding.shares();
                
                totalAssets += currentValue;
                totalDailyChange += dailyChange;
            } else {
                log.warn("가격 정보를 찾을 수 없습니다: {}", holding.symbol());
                totalAssets += holding.totalValue();
            }
        }

        double totalDailyReturn = totalAssets > 0 ? (totalDailyChange / totalAssets) * 100 : 0.0;

        return new PortfolioSummaryTotals(totalAssets, totalDailyReturn, totalDailyChange);
    }

    private record PortfolioSummaryTotals(double totalAssets, double totalDailyReturn, double totalDailyChange) {}

    /**
     * 새로운 포트폴리오 생성
     *
     * @param request 포트폴리오 생성 폼
     * @return 생성된 포트폴리오 결과
     */
    @Transactional
    public CreatePortfolioResult createPortfolio(Long userId, CreatePortfolioRequest request) {
        log.info("Creating portfolio for userId: {}, name: {}", userId, request.name());

        // 1. Rules를 MongoDB에 저장 (선택 사항)
        String ruleId = null;
        if (request.rules() != null) {
            // Holdings 분석하여 벤치마크 결정
            List<Holding> holdingsForAnalysis = convertHoldingsFromRequest(request.holdings());
            BenchmarkIndex determinedBenchmark = benchmarkDeterminerService.determineBenchmark(holdingsForAnalysis);
            
            Rules rules = createRulesFromRequest(request.rules(), determinedBenchmark.getCode());
            Rules savedRules = rulesRepository.save(rules);
            ruleId = savedRules.getId();
            log.info("Rules saved to MongoDB with benchmark {} -> ruleId: {}", determinedBenchmark.getCode(), ruleId);
        } else {
            log.info("No rules provided. Skipping rules save.");
        }

        // 2. Portfolio 생성 (MongoDB에서 받은 ruleId 사용)
        Portfolio portfolio = Portfolio.create(
                request.name(),
                request.description(),
                ruleId, // MongoDB에서 받은 ruleId (없을 수 있음)
                false, // 새로 생성하는 포트폴리오는 메인 포트폴리오가 아님
                userId
        );
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        // 3. holdings 테이블에 보유 종목 저장
        if (request.holdings() != null && !request.holdings().isEmpty()) {
            for (CreatePortfolioRequest.HoldingRequest holdingRequest : request.holdings()) {
                Holding holding = Holding.create(
                        savedPortfolio.id(),
                        holdingRequest.symbol(),
                        holdingRequest.shares(),
                        holdingRequest.currentPrice(),
                        holdingRequest.totalValue(),
                        holdingRequest.change(),
                        holdingRequest.changePercent(),
                        holdingRequest.weight()
                );
                portfolioRepository.saveHolding(holding);
            }
        }

        // 4. 즉시 응답 데이터 생성
        CreatePortfolioResult result = new CreatePortfolioResult(
                savedPortfolio.id(),
                savedPortfolio.name(),
                savedPortfolio.description(),
                savedPortfolio.ruleId(),
                request.totalValue(),
                request.holdings().size(),
                savedPortfolio.status().name()
        );

        // 5. 포트폴리오 생성 완료 이벤트 발행 (트랜잭션 커밋 후)
        applicationEventPublisher.publishEvent(new PortfolioCreatedEvent(savedPortfolio.id()));

        return result;
    }


    private Rules createRulesFromRequest(CreatePortfolioRequest.RulesRequest rulesRequest, String benchmarkCode) {
        List<Rules.RuleItem> rebalanceItems = getRuleItems(rulesRequest.rebalance());
        List<Rules.RuleItem> stopLossItems = getRuleItems(rulesRequest.stopLoss());
        List<Rules.RuleItem> takeProfitItems = getRuleItems(rulesRequest.takeProfit());

        return new Rules(
                rulesRequest.memo(),
                rebalanceItems,
                stopLossItems,
                takeProfitItems,
                benchmarkCode
        );
    }

    private static List<Rules.RuleItem> getRuleItems(List<CreatePortfolioRequest.RuleItemRequest> rulesRequest) {
        if (rulesRequest == null) {
            return List.of();
        }
        return rulesRequest.stream()
                .map(item -> new Rules.RuleItem(item.category(), item.threshold(), item.description()))
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 메인 포트폴리오 요약 정보 조회 (일간 변동률 포함)
     *
     * @param userId 사용자 식별자
     * @return 해당 사용자의 메인 포트폴리오 요약 정보 (이름, 총 가치, 보유 종목 요약, 일간 변동률)
     */
    public PortfolioShortResponse getMainPortfolioShort(Long userId) {
        log.info("Getting main portfolio short info for userId: {}", userId);

        Portfolio mainPortfolio = portfolioRepository.findMainPortfolioByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Main Portfolio", "userId", userId));

        return getPortfolioShort(mainPortfolio.id());
    }

    /**
     * 포트폴리오 요약 정보 조회 (일간 변동률 포함)
     *
     * @param portfolioId 포트폴리오 식별자
     * @return 해당 포트폴리오의 요약 정보 (이름, 총 가치, 보유 종목 요약, 일간 변동률)
     */
    public PortfolioShortResponse getPortfolioShort(Long portfolioId) {
        log.info("Getting portfolio short info for portfolioId: {}", portfolioId);

        PortfolioData data = getPortfolioData(portfolioId);

        if (data.holdings().isEmpty()) {
            return new PortfolioShortResponse(
                    data.portfolio().name(),
                    0.0,
                    List.of(),
                    0.0
            );
        }

        List<PortfolioShortResponse.HoldingSummary> holdingSummaries = data.holdings().stream()
                .map(holding -> createHoldingSummaryWithMaps(holding, data.stockMap(), data.priceMap()))
                .collect(Collectors.toList());

        PortfolioTotals totals = computeTotalsFromHoldingsWithPriceMap(data.holdings(), data.priceMap());
        return new PortfolioShortResponse(
                data.portfolio().name(),
                totals.totalAssets,
                holdingSummaries,
                totals.dailyChange
        );
    }

    private PortfolioShortResponse.HoldingSummary createHoldingSummaryWithMaps(
            Holding holding, 
            Map<String, Stock> stockMap, 
            Map<String, StockService.StockPriceInfo> priceMap) {
        try {
            // Stock 정보를 Map에서 조회 (DB 쿼리 없음)
            Stock stock = stockMap.get(holding.symbol());
            if (stock == null) {
                log.warn("Stock not found for ticker: {}", holding.symbol());
                return new PortfolioShortResponse.HoldingSummary(
                        "Unknown Stock",
                        holding.weight(),
                        0.0
                );
            }

            StockService.StockPriceInfo priceInfo = priceMap.get(holding.symbol());
            if (priceInfo == null) {
                log.warn("가격 정보를 찾을 수 없습니다: {}", holding.symbol());
                return new PortfolioShortResponse.HoldingSummary(
                        stock.getName(),
                        holding.weight(),
                        0.0
                );
            }

            // KIS API에서 가져온 일간 변동률 사용
            double dailyRate = priceInfo.dailyChangeRate();

            return new PortfolioShortResponse.HoldingSummary(
                    stock.getName(),
                    holding.weight(),
                    dailyRate
            );
        } catch (Exception e) {
            log.warn("Failed to get stock information for holding: {}, error: {}", holding.symbol(), e.getMessage());
            return new PortfolioShortResponse.HoldingSummary(
                    "Unknown Stock",
                    holding.weight(),
                    0.0
            );
        }
    }




    /**
     * 포트폴리오 상세 정보 조회 (보유 종목 상세 포함 + Rules 정보)
     *
     * @param portfolioId 포트폴리오 식별자
     * @return 해당 포트폴리오의 상세 정보 (포트폴리오 ID, 보유 종목 상세, 룰 ID, Rules 내용)
     */
    public PortfolioLongResponse getPortfolioLong(Long portfolioId) {
        log.info("Getting portfolio long info for portfolioId: {}", portfolioId);

        PortfolioData data = getPortfolioData(portfolioId);
        
        // Rules 정보 조회
        PortfolioLongResponse.RulesDetail rulesDetail = null;
        if (data.portfolio().ruleId() != null && !data.portfolio().ruleId().trim().isEmpty()) {
            try {
                Optional<Rules> rulesOptional = rulesRepository.findById(data.portfolio().ruleId());
                if (rulesOptional.isPresent()) {
                    Rules rules = rulesOptional.get();
                    rulesDetail = convertRulesToDetail(rules);
                }
            } catch (Exception e) {
                log.warn("Failed to load rules for ruleId: {}, error: {}", data.portfolio().ruleId(), e.getMessage());
            }
        }

        List<PortfolioLongResponse.HoldingDetail> holdingDetails = List.of();
        if (!data.holdings().isEmpty()) {
            holdingDetails = data.holdings().stream()
                    .map(holding -> createHoldingDetailWithMaps(holding, data.stockMap(), data.priceMap()))
                    .collect(Collectors.toList());
        }

        return new PortfolioLongResponse(
                data.portfolio().id(),
                holdingDetails,
                data.portfolio().ruleId(),
                rulesDetail
        );
    }

    /**
     * Rules 도메인 객체를 PortfolioLongResponse RulesDetail로 변환
     */
    private PortfolioLongResponse.RulesDetail convertRulesToDetail(Rules rules) {
        PortfolioLongResponse.BenchmarkDetail benchmarkDetail = null;
        
        // basicBenchmark가 있으면 BenchmarkIndex 정보도 포함
        if (rules.getBasicBenchmark() != null && !rules.getBasicBenchmark().trim().isEmpty()) {
            BenchmarkIndex benchmarkIndex = BenchmarkIndex.fromCode(rules.getBasicBenchmark());
            if (benchmarkIndex != null) {
                benchmarkDetail = new PortfolioLongResponse.BenchmarkDetail(
                        benchmarkIndex.getCode(),
                        benchmarkIndex.getName(),
                        benchmarkIndex.getDescription()
                );
            }
        }
        
        return new PortfolioLongResponse.RulesDetail(
                rules.getId(),
                rules.getMemo(),
                rules.getBasicBenchmark(),
                benchmarkDetail,
                convertRuleItems(rules.getRebalance()),
                convertRuleItems(rules.getStopLoss()),
                convertRuleItems(rules.getTakeProfit()),
                rules.getCreatedAt(),
                rules.getUpdatedAt()
        );
    }

    /**
     * Rules.RuleItem 리스트를 PortfolioLongResponse.RuleItemDetail 리스트로 변환
     */
    private List<PortfolioLongResponse.RuleItemDetail> convertRuleItems(List<Rules.RuleItem> ruleItems) {
        if (ruleItems == null) {
            return List.of();
        }
        return ruleItems.stream()
                .map(item -> new PortfolioLongResponse.RuleItemDetail(
                        item.getCategory(),
                        item.getThreshold(),
                        item.getDescription()
                ))
                .collect(Collectors.toList());
    }

    private PortfolioLongResponse.HoldingDetail createHoldingDetailWithMaps(
            Holding holding, 
            Map<String, Stock> stockMap, 
            Map<String, StockService.StockPriceInfo> priceMap) {
        try {
            // Stock 정보를 Map에서 조회 (DB 쿼리 없음)
            Stock stock = stockMap.get(holding.symbol());
            if (stock == null) {
                log.warn("Stock not found for ticker: {}", holding.symbol());
                return new PortfolioLongResponse.HoldingDetail(
                        "Unknown Stock",
                        holding.weight(),
                        holding.totalValue(),
                        0.0
                );
            }

            StockService.StockPriceInfo priceInfo = priceMap.get(holding.symbol());
            if (priceInfo == null) {
                log.warn("가격 정보를 찾을 수 없습니다: {}", holding.symbol());
                return new PortfolioLongResponse.HoldingDetail(
                        stock.getName(),
                        holding.weight(),
                        holding.totalValue(),
                        0.0
                );
            }

            // KIS API에서 가져온 현재가와 일간 변동률 사용
            double currentPrice = priceInfo.currentPrice();
            double dailyRate = priceInfo.dailyChangeRate();

            // 현재 가치 계산 (수량 * 현재가)
            double currentValue = holding.shares() * currentPrice;

            return new PortfolioLongResponse.HoldingDetail(
                    stock.getName(),
                    holding.weight(),
                    currentValue,
                    dailyRate
            );
        } catch (Exception e) {
            log.warn("Failed to get stock information for holding: {}, error: {}", holding.symbol(), e.getMessage());
            return new PortfolioLongResponse.HoldingDetail(
                    "Unknown Stock",
                    holding.weight(),
                    holding.totalValue(),
                    0.0
            );
        }
    }


    /**
     * 사용자 포트폴리오 리스트 조회
     *
     * @param userId 사용자 식별자
     * @return 해당 사용자의 포트폴리오 리스트 (포트폴리오별 요약 정보 포함)
     */
    public PortfolioListResponse getPortfolioList(Long userId) {
        log.info("Getting portfolio list for userId: {}", userId);

        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        if (portfolios.isEmpty()) {
            return new PortfolioListResponse(List.of());
        }

        // 모든 포트폴리오의 모든 종목을 수집
        List<Holding> allHoldings = portfolios.stream()
                .flatMap(portfolio -> portfolioRepository.findHoldingsByPortfolioId(portfolio.id()).stream())
                .toList();

        if (allHoldings.isEmpty()) {
            return new PortfolioListResponse(portfolios.stream()
                    .map(portfolio -> new PortfolioListResponse.PortfolioListItem(
                            portfolio.id(),
                            portfolio.name(),
                            portfolio.description(),
                            List.of(),
                            0.0,
                            0.0,
                            0.0
                    ))
                    .collect(Collectors.toList()));
        }

        // 모든 종목의 티커를 수집
        List<String> allTickers = allHoldings.stream()
                .map(Holding::symbol)
                .distinct()
                .collect(Collectors.toList());

        // KIS API로 한 번에 가격 정보 조회
        Map<String, StockService.StockPriceInfo> priceMap = stockService.getMultiCurrentPrices(allTickers);

        List<PortfolioListResponse.PortfolioListItem> portfolioItems = portfolios.stream()
                .map(portfolio -> createPortfolioListItemWithPriceMap(portfolio, priceMap))
                .collect(Collectors.toList());

        return new PortfolioListResponse(portfolioItems);
    }

    private PortfolioListResponse.PortfolioListItem createPortfolioListItemWithPriceMap(
            Portfolio portfolio,
            Map<String, StockService.StockPriceInfo> priceMap) {
        List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolio.id());
        
        if (holdings.isEmpty()) {
            return new PortfolioListResponse.PortfolioListItem(
                    portfolio.id(),
                    portfolio.name(),
                    portfolio.description(),
                    List.of(),
                    0.0,
                    0.0,
                    0.0
            );
        }

        // KIS API 데이터를 사용하여 통합 자산 계산
        PortfolioTotals totals = computeTotalsFromHoldingsWithPriceMap(holdings, priceMap);
        double totalAssets = totals.totalAssets;
        double dailyRate = totals.dailyReturnPercent;
        double dailyChange = totals.dailyChange;

        // Holding stocks 정보 가져오기 (priceMap 사용)
        List<PortfolioListResponse.HoldingStock> holdingStocks = getHoldingStocksWithPriceMap(portfolio.id(), priceMap);

        return new PortfolioListResponse.PortfolioListItem(
                portfolio.id(),
                portfolio.name(),
                portfolio.description(),
                holdingStocks,
                totalAssets,
                dailyRate,
                dailyChange
        );
    }

    private List<PortfolioListResponse.HoldingStock> getHoldingStocksWithPriceMap(Long portfolioId, Map<String, StockService.StockPriceInfo> priceMap) {
        try {
            List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolioId);
            
            if (holdings.isEmpty()) {
                return List.of();
            }

            // 모든 티커를 수집하여 한 번에 Stock 정보 조회
            List<String> tickers = holdings.stream()
                    .map(Holding::symbol)
                    .distinct()
                    .collect(Collectors.toList());

            Map<String, Stock> stockMap = stockService.getStocksByTickers(tickers)
                    .stream()
                    .collect(Collectors.toMap(Stock::getTicker, stock -> stock));

            return holdings.stream()
                    .map(holding -> {
                        try {
                            Stock stock = stockMap.get(holding.symbol());
                            if (stock == null) {
                                log.warn("Stock not found for ticker: {}", holding.symbol());
                                return new PortfolioListResponse.HoldingStock(
                                        "Unknown",
                                        "Unknown Stock",
                                        holding.weight(),
                                        holding.totalValue(),
                                        0.0,
                                        0.0
                                );
                            }

                            StockService.StockPriceInfo priceInfo = priceMap.get(holding.symbol());
                            
                            if (priceInfo == null) {
                                log.warn("가격 정보를 찾을 수 없습니다: {}", holding.symbol());
                                return new PortfolioListResponse.HoldingStock(
                                        stock.getTicker(),
                                        stock.getName(),
                                        holding.weight(),
                                        holding.totalValue(),
                                        0.0,
                                        0.0
                                );
                            }
                            
                            // 현재가와 일간 변동률을 사용하여 계산
                            double currentPrice = priceInfo.currentPrice();
                            double dailyRate = priceInfo.dailyChangeRate();
                            
                            // 현재 가치 계산 (수량 * 현재가)
                            double value = holding.shares() * currentPrice;
                            
                            return new PortfolioListResponse.HoldingStock(
                                    stock.getTicker(),
                                    stock.getName(),
                                    holding.weight(),
                                    value,
                                    dailyRate,
                                    currentPrice
                            );
                        } catch (Exception e) {
                            log.warn("Failed to get stock information for holding: {}, error: {}", holding.symbol(), e.getMessage());
                            return new PortfolioListResponse.HoldingStock(
                                    "Unknown",
                                    "Unknown Stock",
                                    holding.weight(),
                                    holding.totalValue(),
                                    0.0,
                                    0.0
                            );
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get holding stocks for portfolio: {}, error: {}", portfolioId, e.getMessage());
            return List.of();
        }
    }

    private static class PortfolioTotals {
        final double totalAssets;
        final double dailyChange;
        final double dailyReturnPercent;

        PortfolioTotals(double totalAssets, double dailyChange, double dailyReturnPercent) {
            this.totalAssets = totalAssets;
            this.dailyChange = dailyChange;
            this.dailyReturnPercent = dailyReturnPercent;
        }
    }

    private record PortfolioData(
            Portfolio portfolio,
            List<Holding> holdings,
            Map<String, Stock> stockMap,
            Map<String, StockService.StockPriceInfo> priceMap
    ) {}

    /**
     * 포트폴리오의 공통 데이터를 조회합니다 (Portfolio, Holdings, Stock 정보, 가격 정보)
     *
     * @param portfolioId 포트폴리오 식별자
     * @return 포트폴리오 공통 데이터 객체
     */
    private PortfolioData getPortfolioData(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", "id", portfolioId));

        List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolioId);

        if (holdings.isEmpty()) {
            return new PortfolioData(portfolio, holdings, Map.of(), Map.of());
        }

        // 모든 종목의 티커를 수집
        List<String> tickers = holdings.stream()
                .map(Holding::symbol)
                .distinct()
                .collect(Collectors.toList());

        // Stock 정보를 한 번에 조회 (N+1 문제 해결)
        Map<String, Stock> stockMap = stockService.getStocksByTickers(tickers)
                .stream()
                .collect(Collectors.toMap(Stock::getTicker, stock -> stock));

        // KIS API로 한 번에 가격 정보 조회
        Map<String, StockService.StockPriceInfo> priceMap = stockService.getMultiCurrentPrices(tickers);

        return new PortfolioData(portfolio, holdings, stockMap, priceMap);
    }

    private PortfolioTotals computeTotalsFromHoldingsWithPriceMap(List<Holding> holdings, Map<String, StockService.StockPriceInfo> priceMap) {
        double totalAssets = 0.0;
        double dailyChange = 0.0;
        
        for (Holding holding : holdings) {
            StockService.StockPriceInfo priceInfo = priceMap.get(holding.symbol());
            
            if (priceInfo != null) {
                double currentPrice = priceInfo.currentPrice();
                double currentValue = currentPrice * holding.shares();
                double holdingDailyChange = priceInfo.dailyChangePrice() * holding.shares();
                
                totalAssets += currentValue;
                dailyChange += holdingDailyChange;
            } else {
                log.warn("가격 정보를 찾을 수 없습니다: {}", holding.symbol());
                // 가격 정보가 없는 경우 holding의 totalValue 사용
                totalAssets += holding.totalValue();
            }
        }
        
        double dailyReturnPercent = totalAssets > 0 ? (dailyChange / totalAssets) * 100 : 0.0;
        return new PortfolioTotals(totalAssets, dailyChange, dailyReturnPercent);
    }

    /**
     * CreatePortfolioRequest의 Holdings를 Holding 도메인 객체로 변환
     * (벤치마크 분석을 위해 임시 생성)
     */
    private List<Holding> convertHoldingsFromRequest(List<CreatePortfolioRequest.HoldingRequest> holdingRequests) {
        if (holdingRequests == null || holdingRequests.isEmpty()) {
            return List.of();
        }
        
        return holdingRequests.stream()
                .map(holdingRequest -> new Holding(
                        null, // temporary id
                        null, // temporary portfolioId  
                        holdingRequest.symbol(),
                        holdingRequest.shares(),
                        holdingRequest.currentPrice(),
                        holdingRequest.totalValue(),
                        holdingRequest.change(),
                        holdingRequest.changePercent(),
                        holdingRequest.weight(),
                        null, // temporary createdAt
                        null  // temporary updatedAt
                ))
                .toList();
    }

    /**
     * 포트폴리오 상태 업데이트
     */
    @Transactional
    public void updatePortfolioStatus(Long portfolioId, Portfolio.PortfolioStatus status) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        Portfolio updatedPortfolio = portfolio.withStatus(status);
        portfolioRepository.save(updatedPortfolio);
        
        log.info("Updated portfolio status - portfolioId: {}, status: {}", portfolioId, status);
    }

    /**
     * 포트폴리오 분석 결과 저장
     */
    @Transactional
    public void savePortfolioAnalysisResult(Long portfolioId, String analysisResult) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        Portfolio updatedPortfolio = portfolio.withStatusAndResult(
                Portfolio.PortfolioStatus.COMPLETED, 
                analysisResult
        );
        portfolioRepository.save(updatedPortfolio);
        
        log.info("Saved portfolio analysis result - portfolioId: {}, result length: {}", 
                portfolioId, analysisResult != null ? analysisResult.length() : 0);
    }

    /**
     * 포트폴리오 ID로 포트폴리오 조회
     */
    @Transactional(readOnly = true)
    public Portfolio getPortfolioById(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
    }
}
