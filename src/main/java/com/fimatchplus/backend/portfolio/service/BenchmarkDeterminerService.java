package com.fimatchplus.backend.portfolio.service;

import com.fimatchplus.backend.portfolio.domain.BenchmarkIndex;
import com.fimatchplus.backend.portfolio.domain.Holding;
import com.fimatchplus.backend.stock.domain.Stock;
import com.fimatchplus.backend.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 벤치마크 결정 서비스
 * 포트폴리오 Holdings를 분석하여 적절한 벤치마크를 자동 결정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkDeterminerService {

    private final StockRepository stockRepository;

    /**
     * 포트폴리오 Holdings를 분석하여 벤치마크 결정
     * @param holdings 포트폴리오 보유 종목
     * @return 결정된 벤치마크 지수
     */
    public BenchmarkIndex determineBenchmark(List<Holding> holdings) {
        log.info("Starting benchmark determination for {} holdings", holdings.size());
        
        try {
            List<String> stockCodes = holdings.stream()
                    .map(Holding::symbol)
                    .collect(Collectors.toList());
            
            if (stockCodes.isEmpty()) {
                log.warn("No stock codes found in holdings");
                return BenchmarkIndex.KOSPI;
            }
            
            MarketAnalysis analysis = analyzePortfolioMarkets(stockCodes);
            log.info("Portfolio market analysis: KOSPI={}, KOSDAQ={}, OTHER={}", 
                    analysis.kospiCount, analysis.kosdaqCount, analysis.otherCount);
            
            BenchmarkIndex selectedBenchmark = selectBenchmark(analysis);
            log.info("Selected benchmark: {}", selectedBenchmark.getCode());
            
            return selectedBenchmark;
            
        } catch (Exception e) {
            log.error("Error during benchmark determination", e);
            return BenchmarkIndex.KOSPI;
        }
    }
    
    /**
     * 포트폴리오 시장 분석
     * 종목 코드를 기반으로 시장별 분류 수행
     */
    private MarketAnalysis analyzePortfolioMarkets(List<String> stockCodes) {
        log.debug("Analyzing portfolio markets for {} stock codes", stockCodes.size());
        
        List<Stock> stocks = stockRepository.findByTickerIn(stockCodes);
        
        Map<String, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getTicker, stock -> stock));
        
        int kospiCount = 0;
        int kosdaqCount = 0; 
        int otherCount = 0;
        
        for (String ticker : stockCodes) {
            Stock stock = stockMap.get(ticker);
            
            if (stock == null) {
                log.debug("Stock not found for ticker: {}", ticker);
                otherCount++;
                continue;
            }
            
            String exchange = stock.getExchange();
            if (exchange == null) {
                log.debug("Exchange is null for ticker: {}", ticker);
                otherCount++;
                continue;
            }
            
            String market = exchange.trim().toUpperCase();
            
            if (market.equals("KOSPI") || market.equals("KQ")) {
                kospiCount++;
                log.debug("Stock {} classified as KOSPI", ticker);
            } else if (market.contains("KOSDAQ")) {
                kosdaqCount++;
                log.debug("Stock {} classified as KOSDAQ", ticker);
            } else {
                otherCount++;
                log.debug("Stock {} classified as OTHER (exchange: {})", ticker, exchange);
            }
        }
        
        return new MarketAnalysis(kospiCount, kosdaqCount, otherCount);
    }
    
    /**
     * 시장 분석 결과로부터 벤치마크 선택
     */
    private BenchmarkIndex selectBenchmark(MarketAnalysis analysis) {
        int kospiCount = analysis.kospiCount;
        int kosdaqCount = analysis.kosdaqCount;
        int otherCount = analysis.otherCount;
        int totalCount = kospiCount + kosdaqCount + otherCount;
        
        if (totalCount == 0) {
            log.debug("No stocks found for market analysis, using default KOSPI");
            return BenchmarkIndex.KOSPI;
        }
        
        double kospiRatio = (double) kospiCount / totalCount;
        double kosdaqRatio = (double) kosdaqCount / totalCount;
        
        log.debug("Market ratios: KOSPI={}%, KOSDAQ={}%", 
                String.format("%.2f", kospiRatio * 100), 
                String.format("%.2f", kosdaqRatio * 100));
        
        if (kospiRatio >= 0.6) {
            return BenchmarkIndex.KOSPI;
        } else if (kosdaqRatio >= 0.6) {
            return BenchmarkIndex.KOSDAQ;
        } else if (kospiRatio > kosdaqRatio) {
            return BenchmarkIndex.KOSPI;
        } else if (kosdaqRatio > kospiRatio) {
            return BenchmarkIndex.KOSDAQ;
        } else {
            return BenchmarkIndex.KOSPI;
        }
    }
    
    /**
     * 시장 분석 결과를 담는 레코드
     */
    private record MarketAnalysis(
            int kospiCount,
            int kosdaqCount, 
            int otherCount
    ) {}
}
