package com.stockone19.backend.portfolio.service;

import com.stockone19.backend.common.exception.ResourceNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final RulesRepository rulesRepository;
    private final StockService stockService;

    /**
     * 사용자별 포트폴리오 합계 정보 조회
     *
     * @param userId 사용자 식별자
     * @return 사용자 보유 포트폴리오 총 합계 정보 (총 자산, 일간 수익 금액, 일간 수익률)
     */
    public PortfolioSummaryResponse getPortfolioSummary(Long userId) {
        log.info("Getting portfolio summary for userId: {}", userId);

        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        double totalAssets = 0.0;
        double totalDailyReturn = 0.0;
        double totalDailyChange = 0.0;

        for (Portfolio portfolio : portfolios) {
            List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolio.id());
            PortfolioTotals totals = computeTotalsFromHoldings(holdings);
            totalAssets += totals.totalAssets;
            totalDailyChange += totals.dailyChange;
            // 가중 평균 수익률 계산 (총합 기준)
            if (totals.totalAssets > 0) {
                totalDailyReturn += (totals.dailyReturnPercent * totals.totalAssets / Math.max(totalAssets, 1e-9));
            }
        }

        return new PortfolioSummaryResponse(
                totalAssets,
                totalDailyReturn,
                totalDailyChange
        );
    }

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
            Rules rules = createRulesFromRequest(request.rules());
            Rules savedRules = rulesRepository.save(rules);
            ruleId = savedRules.getId();
            log.info("Rules saved to MongoDB with id: {}", ruleId);
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

        return new CreatePortfolioResult(
                savedPortfolio.id(),
                savedPortfolio.name(),
                savedPortfolio.description(),
                savedPortfolio.ruleId(),
                request.totalValue(),
                request.holdings().size()
        );
    }

    private Rules createRulesFromRequest(CreatePortfolioRequest.RulesRequest rulesRequest) {
        List<Rules.RuleItem> rebalanceItems = getRuleItems(rulesRequest.rebalance());
        List<Rules.RuleItem> stopLossItems = getRuleItems(rulesRequest.stopLoss());
        List<Rules.RuleItem> takeProfitItems = getRuleItems(rulesRequest.takeProfit());

        return new Rules(
                rulesRequest.memo(),
                rebalanceItems,
                stopLossItems,
                takeProfitItems
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

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", "id", portfolioId));

        List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolioId);

        List<PortfolioShortResponse.HoldingSummary> holdingSummaries = holdings.stream()
                .map(this::createHoldingSummary)
                .collect(Collectors.toList());

        PortfolioTotals totals = computeTotalsFromHoldings(holdings);
        return new PortfolioShortResponse(
                portfolio.name(),
                totals.totalAssets,
                holdingSummaries,
                totals.dailyChange
        );
    }

    private PortfolioShortResponse.HoldingSummary createHoldingSummary(Holding holding) {
        try {
            // Stock 정보를 PostgreSQL에서 가져오기
            Stock stock = stockService.getStockByTicker(holding.symbol());

            // 현재가 정보를 가져와서 dailyRate 계산
            double currentPrice = stockService.getCurrentPrice(stock.ticker());
            double previousClose = stockService.getPreviousClose(stock.ticker());
            double dailyRate = calculateDailyRate(currentPrice, previousClose);

            return new PortfolioShortResponse.HoldingSummary(
                    stock.name(),
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

    private double calculateDailyRate(double currentPrice, double previousClose) {
        if (previousClose == 0) return 0.0;
        return ((currentPrice - previousClose) / previousClose) * 100;
    }

    /**
     * 포트폴리오 상세 정보 조회 (보유 종목 상세 포함)
     *
     * @param portfolioId 포트폴리오 식별자
     * @return 해당 포트폴리오의 상세 정보 (포트폴리오 ID, 보유 종목 상세, 룰 ID)
     */
    public PortfolioLongResponse getPortfolioLong(Long portfolioId) {
        log.info("Getting portfolio long info for portfolioId: {}", portfolioId);

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", "id", portfolioId));

        List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolioId);

        List<PortfolioLongResponse.HoldingDetail> holdingDetails = holdings.stream()
                .map(this::createHoldingDetail)
                .collect(Collectors.toList());

        return new PortfolioLongResponse(
                portfolio.id(),
                holdingDetails,
                portfolio.ruleId()
        );
    }

    private PortfolioLongResponse.HoldingDetail createHoldingDetail(Holding holding) {
        try {
            // Stock 정보를 PostgreSQL에서 가져오기
            Stock stock = stockService.getStockByTicker(holding.symbol());

            // 현재가 정보를 가져와서 dailyRate 계산
            double currentPrice = stockService.getCurrentPrice(stock.ticker());
            double previousClose = stockService.getPreviousClose(stock.ticker());
            double dailyRate = calculateDailyRate(currentPrice, previousClose);

            return new PortfolioLongResponse.HoldingDetail(
                    stock.name(),
                    holding.weight(),
                    holding.totalValue(),
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

        List<PortfolioListResponse.PortfolioListItem> portfolioItems = portfolios.stream()
                .map(this::createPortfolioListItem)
                .collect(Collectors.toList());

        return new PortfolioListResponse(portfolioItems);
    }

    private PortfolioListResponse.PortfolioListItem createPortfolioListItem(Portfolio portfolio) {
        List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolio.id());
        PortfolioTotals totals = computeTotalsFromHoldings(holdings);
        double totalAssets = totals.totalAssets;
        double dailyRate = totals.dailyReturnPercent;
        double dailyChange = totals.dailyChange;

        // Holding stocks 정보 가져오기
        List<PortfolioListResponse.HoldingStock> holdingStocks = getHoldingStocks(portfolio.id());

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

    private List<PortfolioListResponse.HoldingStock> getHoldingStocks(Long portfolioId) {
        try {
            List<Holding> holdings = portfolioRepository.findHoldingsByPortfolioId(portfolioId);

            return holdings.stream()
                    .map(holding -> {
                        try {
                            Stock stock = stockService.getStockByTicker(holding.symbol());
                            return new PortfolioListResponse.HoldingStock(
                                    stock.ticker(),
                                    stock.name(),
                                    holding.weight()
                            );
                        } catch (Exception e) {
                            log.warn("Failed to get stock information for holding: {}, error: {}", holding.symbol(), e.getMessage());
                            return new PortfolioListResponse.HoldingStock(
                                    "Unknown",
                                    "Unknown Stock",
                                    holding.weight()
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

    private PortfolioTotals computeTotalsFromHoldings(List<Holding> holdings) {
        double totalAssets = 0.0;
        double dailyChange = 0.0;
        for (Holding holding : holdings) {
            try {
                double currentPrice = stockService.getCurrentPrice(holding.symbol());
                double previousClose = stockService.getPreviousClose(holding.symbol());
                totalAssets += currentPrice * holding.shares();
                dailyChange += (currentPrice - previousClose) * holding.shares();
            } catch (Exception e) {
                log.warn("Failed to compute metrics for holding: {} - {}", holding.symbol(), e.getMessage());
            }
        }
        double dailyReturnPercent = totalAssets > 0 ? (dailyChange / totalAssets) * 100 : 0.0;
        return new PortfolioTotals(totalAssets, dailyChange, dailyReturnPercent);
    }
}
