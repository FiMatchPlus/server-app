package com.stockone19.backend.stock.service;

import com.stockone19.backend.stock.dto.StockDetailResponse;
import com.stockone19.backend.stock.dto.StockPriceResponse;
import com.stockone19.backend.stock.dto.StockSearchResponse;
import com.stockone19.backend.stock.dto.StockSearchResult;
import com.stockone19.backend.stock.repository.StockPriceRepository;
import com.stockone19.backend.stock.repository.StockRepository;
import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.domain.StockPrice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;

    public StockService(StockRepository stockRepository, StockPriceRepository stockPriceRepository) {
        this.stockRepository = stockRepository;
        this.stockPriceRepository = stockPriceRepository;
    }

    public StockPriceResponse getStockPrices(List<String> tickers) {
        List<Stock> stocks = findStocksByTickers(tickers);
        List<StockPrice> latestPrices = findLatestPricesByTickers(tickers);
        List<StockPriceResponse.StockPriceData> priceDataList = convertToStockPriceDataList(stocks, latestPrices);

        return StockPriceResponse.success(
                "주식 가격 정보를 성공적으로 조회했습니다.",
                priceDataList
        );
    }

    public StockDetailResponse getStockDetail(String ticker, String interval) {
        Stock stock = getStockByTicker(ticker);
        List<StockDetailResponse.ChartData> chartData = getChartDataForDetail(ticker, interval);
        StockDetailResponse.SummaryData summaryData = createSummaryData(stock, ticker, interval);
        StockDetailResponse.StockDetailData detailData = createStockDetailData(stock, chartData, summaryData);

        return StockDetailResponse.success(
                "종목 상세 정보를 성공적으로 조회했습니다.",
                detailData
        );
    }

    public List<StockDetailResponse.ChartData> getChartData(String stockId, String intervalUnit, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        List<StockPrice> prices = stockPriceRepository.findByStockIdAndInterval(
                stockId, intervalUnit, startDate, endDate, limit
        );

        return convertToChartDataList(prices);
    }

    public void sendRealTimeStockPrice(String ticker, double price) {
        // TODO: WebSocket을 통한 실시간 주가 전송 구현
        // 현재는 로그만 출력
        System.out.println("Real-time price update - Ticker: " + ticker + ", Price: " + price);
    }

    /**
     * 종목 이름 또는 티커로 검색 (가격 정보 포함)
     *
     * @param keyword 검색 키워드 (종목명 또는 티커)
     * @param limit 검색 결과 제한 수 (기본값: 20)
     * @return 검색된 종목 리스트 (가격 정보 포함)
     */
    public StockSearchResponse searchStocks(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return StockSearchResponse.success(List.of());
        }

        List<StockSearchResult> searchResults = stockRepository.searchByNameOrTickerWithPrice(keyword.trim(), limit);
        List<StockSearchResponse.StockSearchData> searchData = searchResults.stream()
                .map(result -> StockSearchResponse.StockSearchData.of(
                        result.ticker(),
                        result.name(),
                        result.industryName(),
                        result.price().doubleValue(),
                        result.changePercent().doubleValue()
                ))
                .collect(Collectors.toList());

        return StockSearchResponse.success(searchData);
    }

    /**
     * 티커로 주식 정보를 조회합니다.
     *
     * @param ticker 종목 티커
     * @return Stock 객체
     * @throws RuntimeException 종목을 찾을 수 없는 경우
     */
    public Stock getStockByTicker(String ticker) {
        return stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다: " + ticker));
    }

    /**
     * 티커로 현재 가격을 조회합니다.
     *
     * @param ticker 종목 티커
     * @return 현재 가격 (double)
     * @throws RuntimeException 종목을 찾을 수 없거나 가격 데이터가 없는 경우
     */
    public double getCurrentPrice(String ticker) {
        StockPrice latestPrice = stockPriceRepository.findLatestByStockIdAndInterval(ticker, "1d");
        if (latestPrice == null) {
            throw new RuntimeException("가격 데이터를 찾을 수 없습니다: " + ticker);
        }
        return latestPrice.closePrice().doubleValue();
    }

    /**
     * 티커로 이전 가격을 조회합니다.
     *
     * @param ticker 종목 티커
     * @return 이전 가격 (double)
     * @throws RuntimeException 종목을 찾을 수 없거나 가격 데이터가 없는 경우
     */
    public double getPreviousClose(String ticker) {
        StockPrice latestPrice = stockPriceRepository.findLatestByStockIdAndInterval(ticker, "1d");
        if (latestPrice == null) {
            throw new RuntimeException("가격 데이터를 찾을 수 없습니다: " + ticker);
        }
        return latestPrice.openPrice().doubleValue();
    }

    // Private helper methods

    private List<Stock> findStocksByTickers(List<String> tickers) {
        return stockRepository.findByTickers(tickers);
    }

    private List<StockPrice> findLatestPricesByTickers(List<String> tickers) {
        return stockPriceRepository.findLatestByStockIdsAndInterval(tickers, "1d");
    }

    private List<StockPriceResponse.StockPriceData> convertToStockPriceDataList(List<Stock> stocks, List<StockPrice> latestPrices) {
        return stocks.stream()
                .map(stock -> convertToStockPriceData(stock, latestPrices))
                .collect(Collectors.toList());
    }

    private StockPriceResponse.StockPriceData convertToStockPriceData(Stock stock, List<StockPrice> latestPrices) {
        StockPrice latestPrice = findLatestPriceForStock(stock.ticker(), latestPrices);

        if (latestPrice == null) {
            return createEmptyStockPriceData(stock);
        }

        return createStockPriceDataFromPrice(stock, latestPrice);
    }

    private StockPrice findLatestPriceForStock(String ticker, List<StockPrice> latestPrices) {
        return latestPrices.stream()
                .filter(price -> price.stockId().equals(ticker))
                .findFirst()
                .orElse(null);
    }

    private StockPriceResponse.StockPriceData createEmptyStockPriceData(Stock stock) {
        return new StockPriceResponse.StockPriceData(
                stock.ticker(),
                stock.name(),
                0.0, 0.0, 0.0, 0.0
        );
    }

    private StockPriceResponse.StockPriceData createStockPriceDataFromPrice(Stock stock, StockPrice latestPrice) {
        double currentPrice = latestPrice.closePrice().doubleValue();
        double previousClose = latestPrice.openPrice().doubleValue();
        double dailyChange = latestPrice.changeAmount().doubleValue();
        double dailyRate = latestPrice.changeRate().doubleValue();

        return new StockPriceResponse.StockPriceData(
                stock.ticker(),
                stock.name(),
                currentPrice,
                previousClose,
                dailyRate,
                dailyChange
        );
    }


    private List<StockDetailResponse.ChartData> getChartDataForDetail(String ticker, String interval) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(30);
        List<StockPrice> chartPrices = stockPriceRepository.findByStockIdAndInterval(
                ticker, interval, startDate, endDate, 100
        );

        return convertToChartDataList(chartPrices);
    }

    private List<StockDetailResponse.ChartData> convertToChartDataList(List<StockPrice> prices) {
        return prices.stream()
                .map(this::convertToChartData)
                .collect(Collectors.toList());
    }

    private StockDetailResponse.ChartData convertToChartData(StockPrice price) {
        return new StockDetailResponse.ChartData(
                price.datetime().toInstant(ZoneOffset.UTC),
                price.openPrice().doubleValue(),
                price.closePrice().doubleValue(),
                price.highPrice().doubleValue(),
                price.lowPrice().doubleValue(),
                price.volume()
        );
    }

    private StockDetailResponse.SummaryData createSummaryData(Stock stock, String ticker, String interval) {
        StockPrice latestPrice = stockPriceRepository.findLatestByStockIdAndInterval(ticker, interval);

        if (latestPrice == null) {
            return createEmptySummaryData(stock);
        }

        return createSummaryDataFromPrice(stock, latestPrice);
    }

    private StockDetailResponse.SummaryData createEmptySummaryData(Stock stock) {
        return new StockDetailResponse.SummaryData(
                stock.ticker(),
                stock.name(),
                0.0, 0.0, 0.0, 0.0, 0L, 0.0
        );
    }

    private StockDetailResponse.SummaryData createSummaryDataFromPrice(Stock stock, StockPrice latestPrice) {
        return new StockDetailResponse.SummaryData(
                stock.ticker(),
                stock.name(),
                latestPrice.closePrice().doubleValue(),
                latestPrice.openPrice().doubleValue(),
                latestPrice.changeRate().doubleValue(),
                latestPrice.changeAmount().doubleValue(),
                latestPrice.volume(),
                0.0 // marketCap은 별도 계산 필요
        );
    }

    private StockDetailResponse.StockDetailData createStockDetailData(
            Stock stock,
            List<StockDetailResponse.ChartData> chartData,
            StockDetailResponse.SummaryData summaryData) {
        return new StockDetailResponse.StockDetailData(
                stock.ticker(),
                stock.name(),
                stock.engName(),
                stock.exchange(),
                stock.industryName(),
                chartData,
                summaryData
        );
    }
}
