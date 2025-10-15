package com.stockone19.backend.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.portfolio.domain.BenchmarkIndex;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.Rules;
import com.stockone19.backend.portfolio.dto.*;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import com.stockone19.backend.portfolio.repository.RulesRepository;
import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class PortfolioQueryService {

    private final PortfolioRepository portfolioRepository;
    private final RulesRepository rulesRepository;
    private final StockService stockService;
    private final PortfolioCalculator portfolioCalculator;
    private final ObjectMapper objectMapper;

    /**
     * 사용자별 포트폴리오 합계 정보 조회
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PortfolioSummaryResponse getPortfolioSummary(Long userId) {
        log.info("Getting portfolio summary for userId: {}", userId);

        List<Holding> allHoldings = getAllUserHoldingsWithTransaction(userId);
        if (allHoldings.isEmpty()) {
            return new PortfolioSummaryResponse(0.0, 0.0, 0.0);
        }

        try {
            Map<String, StockService.StockPriceInfo> priceMap = getPriceMapForHoldings(allHoldings);
            PortfolioCalculator.PortfolioTotals totals = calculatePortfolioSummaryTotals(allHoldings, priceMap);

            return new PortfolioSummaryResponse(
                    totals.totalAssets(),
                    totals.dailyReturnPercent(),
                    totals.dailyChange()
            );
        } catch (Exception e) {
            log.error("KIS API 호출 실패, DB 가격 데이터 사용: {}", e.getMessage());
            return getPortfolioSummaryFromStoredData(allHoldings);
        }
    }

    /**
     * 사용자의 메인 포트폴리오 요약 정보 조회
     */
    public PortfolioShortResponse getMainPortfolioShort(Long userId) {
        log.info("Getting main portfolio short info for userId: {}", userId);

        Portfolio mainPortfolio = portfolioRepository.findMainPortfolioByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Main Portfolio", "userId", userId));

        return getPortfolioShort(mainPortfolio.id());
    }

    /**
     * 포트폴리오 요약 정보 조회
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

        PortfolioCalculator.PortfolioTotals totals = portfolioCalculator.calculateTotals(data.holdings(), data.priceMap());
        return new PortfolioShortResponse(
                data.portfolio().name(),
                totals.totalAssets(),
                holdingSummaries,
                totals.dailyChange()
        );
    }

    /**
     * 포트폴리오 상세 정보 조회
     */
    public PortfolioLongResponse getPortfolioLong(Long portfolioId) {
        log.info("Getting portfolio long info for portfolioId: {}", portfolioId);

        PortfolioData data = getPortfolioData(portfolioId);

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

        PortfolioLongResponse.AnalysisDetail analysisDetail = getAnalysisDetail(portfolioId, data);

        return new PortfolioLongResponse(
                data.portfolio().id(),
                data.portfolio().name(),
                data.portfolio().description(),
                holdingDetails,
                data.portfolio().ruleId(),
                rulesDetail,
                analysisDetail
        );
    }

    private PortfolioLongResponse.AnalysisDetail getAnalysisDetail(Long portfolioId, PortfolioData data) {
        PortfolioLongResponse.AnalysisDetail analysisDetail = null;
        if (data.portfolio().analysisResult() != null && !data.portfolio().analysisResult().trim().isEmpty()) {
            try {
                analysisDetail = convertAnalysisResultToDetail(
                        data.portfolio().analysisResult(),
                        data.portfolio().status()
                );
            } catch (Exception e) {
                log.warn("Failed to parse analysis result for portfolioId: {}, error: {}", portfolioId, e.getMessage());
            }
        }
        return analysisDetail;
    }

    /**
     * 사용자 포트폴리오 리스트 조회
     */
    public PortfolioListResponse getPortfolioList(Long userId) {
        log.info("Getting portfolio list for userId: {}", userId);

        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        if (portfolios.isEmpty()) {
            return new PortfolioListResponse(List.of());
        }

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

        List<String> allTickers = allHoldings.stream()
                .map(Holding::symbol)
                .distinct()
                .collect(Collectors.toList());

        Map<String, StockService.StockPriceInfo> priceMap = stockService.getMultiCurrentPrices(allTickers);

        List<PortfolioListResponse.PortfolioListItem> portfolioItems = portfolios.stream()
                .map(portfolio -> createPortfolioListItemWithPriceMap(portfolio, priceMap))
                .collect(Collectors.toList());

        return new PortfolioListResponse(portfolioItems);
    }

    /**
     * 포트폴리오 분석 결과만 조회 (AnalysisDetail)
     */
    public PortfolioLongResponse.AnalysisDetail getPortfolioAnalysisDetail(Long portfolioId) {
        log.info("Getting portfolio analysis detail for portfolioId: {}", portfolioId);
        
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", "id", portfolioId));
        
        if (portfolio.analysisResult() == null || portfolio.analysisResult().trim().isEmpty()) {
            return new PortfolioLongResponse.AnalysisDetail(
                    convertPortfolioStatusToAnalysisStatus(portfolio.status()),
                    null
            );
        }
        
        try {
            return convertAnalysisResultToDetail(portfolio.analysisResult(), portfolio.status());
        } catch (Exception e) {
            log.error("Failed to parse analysis result for portfolioId: {}, error: {}", portfolioId, e.getMessage());
            throw new RuntimeException("Failed to parse analysis result", e);
        }
    }

    /**
     * 포트폴리오 분석 상태 조회
     * 분석 진행 상태를 추적하기 위한 메서드
     */
    public PortfolioStatusResponse getPortfolioStatus(Long portfolioId) {
        log.info("Getting portfolio status for portfolioId: {}", portfolioId);
        
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", "id", portfolioId));
        
        return PortfolioStatusResponse.from(portfolio);
    }

    /**
     * 포트폴리오 상세 정보 조회
     */
    public PortfolioDetailResponse getPortfolioDetail(Long portfolioId) {
        log.info("Getting portfolio detail for portfolioId: {}", portfolioId);

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", "id", portfolioId));

        List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolioId);

        Map<String, Stock> stockMap = Map.of();
        if (!holdings.isEmpty()) {
            List<String> tickers = holdings.stream()
                    .map(Holding::symbol)
                    .distinct()
                    .collect(Collectors.toList());

            stockMap = stockService.getStocksByTickers(tickers)
                    .stream()
                    .collect(Collectors.toMap(Stock::getTicker, stock -> stock));
        }

        Map<String, Stock> finalStockMap = stockMap;
        List<PortfolioDetailResponse.HoldingResponse> holdingResponses = holdings.stream()
                .map(holding -> convertHoldingToResponse(holding, finalStockMap))
                .collect(Collectors.toList());

        double totalValue = holdings.stream()
                .mapToDouble(Holding::totalValue)
                .sum();

        return new PortfolioDetailResponse(
                portfolio.id(),
                portfolio.name(),
                totalValue,
                portfolio.description(),
                holdingResponses
        );
    }


    @Transactional(readOnly = true, timeout = 5)
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

    private PortfolioCalculator.PortfolioTotals calculatePortfolioSummaryTotals(List<Holding> holdings, Map<String, StockService.StockPriceInfo> priceMap) {
        return portfolioCalculator.calculateTotals(holdings, priceMap);
    }

    private record PortfolioData(
            Portfolio portfolio,
            List<Holding> holdings,
            Map<String, Stock> stockMap,
            Map<String, StockService.StockPriceInfo> priceMap
    ) {}

    private PortfolioData getPortfolioData(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", "id", portfolioId));

        List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolioId);

        if (holdings.isEmpty()) {
            return new PortfolioData(portfolio, holdings, Map.of(), Map.of());
        }

        List<String> tickers = holdings.stream()
                .map(Holding::symbol)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Stock> stockMap = stockService.getStocksByTickers(tickers)
                .stream()
                .collect(Collectors.toMap(Stock::getTicker, stock -> stock));

        Map<String, StockService.StockPriceInfo> priceMap = stockService.getMultiCurrentPrices(tickers);

        return new PortfolioData(portfolio, holdings, stockMap, priceMap);
    }

    private PortfolioShortResponse.HoldingSummary createHoldingSummaryWithMaps(
            Holding holding,
            Map<String, Stock> stockMap,
            Map<String, StockService.StockPriceInfo> priceMap) {
        try {
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

    private PortfolioLongResponse.HoldingDetail createHoldingDetailWithMaps(
            Holding holding,
            Map<String, Stock> stockMap,
            Map<String, StockService.StockPriceInfo> priceMap) {
        try {
            Stock stock = stockMap.get(holding.symbol());
            if (stock == null) {
                log.warn("Stock not found for ticker: {}", holding.symbol());
                return new PortfolioLongResponse.HoldingDetail(
                        holding.symbol(),
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
                        stock.getTicker(),
                        stock.getName(),
                        holding.weight(),
                        holding.totalValue(),
                        0.0
                );
            }

            double currentPrice = priceInfo.currentPrice();
            double dailyRate = priceInfo.dailyChangeRate();

            double currentValue = holding.shares() * currentPrice;

            return new PortfolioLongResponse.HoldingDetail(
                    stock.getTicker(),
                    stock.getName(),
                    holding.weight(),
                    currentValue,
                    dailyRate
            );
        } catch (Exception e) {
            log.warn("Failed to get stock information for holding: {}, error: {}", holding.symbol(), e.getMessage());
            return new PortfolioLongResponse.HoldingDetail(
                    "Unknown Stock",
                    "Unknown Stock",
                    holding.weight(),
                    holding.totalValue(),
                    0.0
            );
        }
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

        PortfolioCalculator.PortfolioTotals totals = portfolioCalculator.calculateTotals(holdings, priceMap);
        double totalAssets = totals.totalAssets();
        double dailyRate = totals.dailyReturnPercent();
        double dailyChange = totals.dailyChange();

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
                                        holding.shares(),
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
                                        holding.shares(),
                                        holding.weight(),
                                        holding.totalValue(),
                                        0.0,
                                        0.0
                                );
                            }

                            double currentPrice = priceInfo.currentPrice();
                            double dailyRate = priceInfo.dailyChangeRate();

                            double value = holding.shares() * currentPrice;

                            return new PortfolioListResponse.HoldingStock(
                                    stock.getTicker(),
                                    stock.getName(),
                                    holding.shares(),
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
                                    holding.shares(),
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

    private PortfolioLongResponse.RulesDetail convertRulesToDetail(Rules rules) {
        PortfolioLongResponse.BenchmarkDetail benchmarkDetail = null;

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

    /**
     * 분석 결과 JSON을 화면용 AnalysisDetail로 변환
     */
    private PortfolioLongResponse.AnalysisDetail convertAnalysisResultToDetail(
            String analysisResultJson,
            Portfolio.PortfolioStatus status
    ) {
        try {
            String analysisStatus = convertPortfolioStatusToAnalysisStatus(status);

            List<PortfolioLongResponse.AnalysisResult> results = null;
            if (status == Portfolio.PortfolioStatus.COMPLETED) {
                PortfolioAnalysisResponse analysisResponse = objectMapper.readValue(
                        analysisResultJson,
                        PortfolioAnalysisResponse.class
                );

                if (analysisResponse != null && analysisResponse.portfolios() != null && !analysisResponse.portfolios().isEmpty()) {
                    results = analysisResponse.portfolios().stream()
                            .map(this::convertStrategyToAnalysisResult)
                            .collect(Collectors.toList());
                }
            }

            return new PortfolioLongResponse.AnalysisDetail(analysisStatus, results);

        } catch (Exception e) {
            log.error("Failed to convert analysis result to detail: {}", e.getMessage());
            throw new RuntimeException("Failed to parse analysis result", e);
        }
    }

    private String convertPortfolioStatusToAnalysisStatus(Portfolio.PortfolioStatus status) {
        return switch (status) {
            case COMPLETED -> "COMPLETED";
            case RUNNING -> "RUNNING";
            case PENDING -> "PENDING";
            case FAILED -> "FAILED";
        };
    }

    private PortfolioLongResponse.AnalysisResult convertStrategyToAnalysisResult(
            PortfolioAnalysisResponse.PortfolioStrategyResponse strategy
    ) {
        String riskLevel = portfolioCalculator.calculateRiskLevel(strategy.metrics().downsideDeviation());

        return new PortfolioLongResponse.AnalysisResult(
                convertTypeName(strategy.type()),
                riskLevel,
                strategy.weights()
        );
    }

    private String convertTypeName(String type) {
        if (type == null) {
            return null;
        }
        
        return switch (type.toLowerCase()) {
            case "user" -> "내 포트폴리오";
            case "min_downside_risk" -> "하방위험 최소화";
            case "max_sortino" -> "소르티노 비율 최적화";
            default -> {
                log.warn("Unknown portfolio type: {}", type);
                yield type;
            }
        };
    }

    private PortfolioDetailResponse.HoldingResponse convertHoldingToResponse(Holding holding, Map<String, Stock> stockMap) {
        String stockName = "Unknown Stock";
        Stock stock = stockMap.get(holding.symbol());
        if (stock != null) {
            stockName = stock.getName();
        }

        return new PortfolioDetailResponse.HoldingResponse(
                holding.symbol(),
                stockName,
                holding.shares(),
                holding.currentPrice(),
                holding.totalValue(),
                holding.changeAmount(),
                holding.changePercent(),
                holding.weight()
        );
    }
}
