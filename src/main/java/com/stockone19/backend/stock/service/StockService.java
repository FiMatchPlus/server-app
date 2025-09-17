package com.stockone19.backend.stock.service;

import com.stockone19.backend.stock.dto.StockDetailResponse;
import com.stockone19.backend.stock.dto.StockPriceResponse;
import com.stockone19.backend.stock.dto.StockSearchResponse;
import com.stockone19.backend.stock.repository.StockPriceRepository;
import com.stockone19.backend.stock.repository.StockRepository;
import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.domain.StockPrice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final KisPriceClient kisPriceClient;

    public StockService(StockRepository stockRepository, StockPriceRepository stockPriceRepository, KisPriceClient kisPriceClient) {
        this.stockRepository = stockRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.kisPriceClient = kisPriceClient;
    }

    public StockPriceResponse getStockPrices(List<String> tickers) {
        List<Stock> stocks = findStocksByTickers(tickers);
        List<StockPrice> latestPrices = findLatestPricesByTickers(tickers);
        List<StockPriceResponse.StockPriceData> priceDataList = convertToStockPriceDataList(stocks, latestPrices);

        return StockPriceResponse.success(priceDataList);
    }

    public StockPriceResponse getCurrentPriceForSingle(String ticker) {
        Stock stock = getStockByTicker(ticker);

        KisQuoteResponse quote = kisPriceClient.fetchQuote(ticker);
        java.util.Map<String, Object> out = quote != null ? quote.output() : null;
        if (out == null) {
            throw new RuntimeException("KIS quote response is empty");
        }

        KisParsed parsed = parseKisOutput(out);
        log.info(
                "KIS quote - status: {}, name: {}, prpr: {}, vrss: {}, sign: {}, ctrt: {}, mcap: {}",
                parsed.status, parsed.korName, parsed.currentPrice, parsed.dailyChange, parsed.sign, parsed.dailyRate, parsed.marketCap
        );

        List<StockPriceResponse.StockPriceData> data = List.of(
                new StockPriceResponse.StockPriceData(
                        stock.getTicker(),
                        stock.getName(),
                        parsed.currentPrice,
                        parsed.dailyRate,
                        parsed.dailyChange,
                        parsed.marketCap
                )
        );

        return StockPriceResponse.success(data);
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private KisParsed parseKisOutput(java.util.Map<String, Object> out) {
        KisParsed parsed = new KisParsed();
        parsed.currentPrice = parseDouble(out.get("stck_prpr"));
        String signCode = String.valueOf(out.get("prdy_vrss_sign"));
        double multiplier = signToMultiplier(signCode);
        parsed.dailyChange = parseDouble(out.get("prdy_vrss")) * multiplier;
        parsed.dailyRate = parseDouble(out.get("prdy_ctrt")) * multiplier;
        parsed.marketCap = parseDouble(out.get("hts_avls"));
        parsed.status = String.valueOf(out.get("iscd_stat_cls_code"));
        parsed.korName = String.valueOf(out.get("bstp_kor_isnm"));
        parsed.sign = signCode;
        return parsed;
    }

    private static class KisParsed {
        double currentPrice;
        double dailyChange;
        double dailyRate;
        double marketCap;
        String status;
        String korName;
        String sign;
    }

    private double signToMultiplier(String signCode) {
        if (signCode == null) return 1.0;
        // KIS 관례: 1/2(상한/상승)=+, 3(보합)=0 영향, 4/5(하락/하한)=-
        return switch (signCode) {
            case "1", "2" -> 1.0;
            case "4", "5" -> -1.0;
            case "3" -> 0.0;
            default -> 1.0;
        };
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
        List<StockPrice> prices = stockPriceRepository.findByStockCodeAndInterval(
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
     * 종목 이름 또는 티커로 검색 (기본 정보만)
     *
     * @param keyword 검색 키워드 (종목명 또는 티커)
     * @param limit 검색 결과 제한 수 (기본값: 20)
     * @return 검색된 종목 리스트 (티커, 종목명, 업종)
     */
    public StockSearchResponse searchStocks(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return StockSearchResponse.success(List.of());
        }

        List<Stock> stocks = stockRepository.searchByNameOrTicker(keyword.trim(), 
                org.springframework.data.domain.PageRequest.of(0, limit));
        
        List<StockSearchResponse.StockSearchData> searchData = stocks.stream()
                .map(stock -> StockSearchResponse.StockSearchData.of(
                        stock.getTicker(),
                        stock.getName(),
                        stock.getIndustryName()
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
     * 여러 티커로 주식 정보를 배치 조회합니다.
     *
     * @param tickers 종목 티커 리스트
     * @return Stock 객체 리스트
     */
    public List<Stock> getStocksByTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }
        return stockRepository.findByTickerIn(tickers);
    }

    /**
     * 티커로 현재 가격을 조회합니다.
     *
     * @param ticker 종목 티커
     * @return 현재 가격 (double)
     * @throws RuntimeException 종목을 찾을 수 없거나 가격 데이터가 없는 경우
     */
    public double getCurrentPrice(String ticker) {
        StockPrice latestPrice = stockPriceRepository.findFirstByStockCodeAndIntervalUnitOrderByDatetimeDesc(ticker, "1d");
        if (latestPrice == null) {
            throw new RuntimeException("가격 데이터를 찾을 수 없습니다: " + ticker);
        }
        return latestPrice.getClosePrice().doubleValue();
    }


    /**
     * 여러 종목의 현재가와 전일종가를 KIS API로 조회합니다.
     *
     * @param tickers 종목 티커 목록
     * @return 종목별 현재가와 전일종가 정보
     */
    public Map<String, StockPriceInfo> getMultiCurrentPrices(List<String> tickers) {
        if (tickers.isEmpty()) {
            return Map.of();
        }

        try {
            KisMultiPriceResponse response = kisPriceClient.fetchMultiPrice(tickers);
            
            if (!"0".equals(response.rtCd())) {
                throw new RuntimeException("KIS API 오류: " + response.msg1());
            }

            Map<String, StockPriceInfo> priceMap = new HashMap<>();
            
            for (KisMultiPriceResponse.ResponseBodyOutput output : response.output()) {
                try {
                    String ticker = output.interShrnIscd();
                    double currentPrice = Double.parseDouble(output.inter2Prpr());
                    String signCode = output.prdyVrssSign();
                    double multiplier = signToMultiplier(signCode);
                    double dailyChangeRate = parseDouble(output.prdyCtrt()) * multiplier;
                    double dailyChangePrice = parseDouble(output.inter2PrdyVrss()) * multiplier;
                    
                    priceMap.put(ticker, new StockPriceInfo(currentPrice, dailyChangeRate, dailyChangePrice));
                } catch (NumberFormatException e) {
                    log.warn("가격 데이터 파싱 오류 - 종목: {}, 현재가: {}, 전일대비: {}", 
                            output.interShrnIscd(), output.inter2Prpr(), output.inter2PrdyVrss());
                }
            }
            
            return priceMap;
        } catch (Exception e) {
            log.error("KIS 다중 가격 조회 오류: {}", e.getMessage());
            throw new RuntimeException("가격 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 종목 가격 정보를 담는 레코드
     */
    public record StockPriceInfo(double currentPrice, double dailyChangeRate, double dailyChangePrice) {}

    // Private helper methods

    private List<Stock> findStocksByTickers(List<String> tickers) {
        return stockRepository.findByTickerIn(tickers);
    }

    private List<StockPrice> findLatestPricesByTickers(List<String> tickers) {
        return stockPriceRepository.findLatestByStockCodesAndInterval(tickers, "1d");
    }

    private List<StockPriceResponse.StockPriceData> convertToStockPriceDataList(List<Stock> stocks, List<StockPrice> latestPrices) {
        return stocks.stream()
                .map(stock -> convertToStockPriceData(stock, latestPrices))
                .collect(Collectors.toList());
    }

    private StockPriceResponse.StockPriceData convertToStockPriceData(Stock stock, List<StockPrice> latestPrices) {
        StockPrice latestPrice = findLatestPriceForStock(stock.getTicker(), latestPrices);

        if (latestPrice == null) {
            return createEmptyStockPriceData(stock);
        }

        return createStockPriceDataFromPrice(stock, latestPrice);
    }

    private StockPrice findLatestPriceForStock(String ticker, List<StockPrice> latestPrices) {
        return latestPrices.stream()
                .filter(price -> price.getStockCode().equals(ticker))
                .findFirst()
                .orElse(null);
    }

    private StockPriceResponse.StockPriceData createEmptyStockPriceData(Stock stock) {
        return new StockPriceResponse.StockPriceData(
                stock.getTicker(),
                stock.getName(),
                0.0, 0.0, 0.0,
                0.0
        );
    }

    private StockPriceResponse.StockPriceData createStockPriceDataFromPrice(Stock stock, StockPrice latestPrice) {
        double currentPrice = latestPrice.getClosePrice().doubleValue();
        double dailyChange = latestPrice.getChangeAmount().doubleValue();
        double dailyRate = latestPrice.getChangeRate().doubleValue();

        return new StockPriceResponse.StockPriceData(
                stock.getTicker(),
                stock.getName(),
                currentPrice,
                dailyRate,
                dailyChange,
                0.0
        );
    }


    private List<StockDetailResponse.ChartData> getChartDataForDetail(String ticker, String interval) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(30);
        List<StockPrice> chartPrices = stockPriceRepository.findByStockCodeAndInterval(
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
                price.getDatetime().toInstant(ZoneOffset.UTC),
                price.getOpenPrice().doubleValue(),
                price.getClosePrice().doubleValue(),
                price.getHighPrice().doubleValue(),
                price.getLowPrice().doubleValue(),
                price.getVolume()
        );
    }

    private StockDetailResponse.SummaryData createSummaryData(Stock stock, String ticker, String interval) {
        StockPrice latestPrice = stockPriceRepository.findFirstByStockCodeAndIntervalUnitOrderByDatetimeDesc(ticker, interval);

        if (latestPrice == null) {
            return createEmptySummaryData(stock);
        }

        return createSummaryDataFromPrice(stock, latestPrice);
    }

    private StockDetailResponse.SummaryData createEmptySummaryData(Stock stock) {
        return new StockDetailResponse.SummaryData(
                stock.getTicker(),
                stock.getName(),
                0.0, 0.0, 0.0, 0L, 0.0
        );
    }

    private StockDetailResponse.SummaryData createSummaryDataFromPrice(Stock stock, StockPrice latestPrice) {
        return new StockDetailResponse.SummaryData(
                stock.getTicker(),
                stock.getName(),
                latestPrice.getClosePrice().doubleValue(),
                latestPrice.getChangeRate().doubleValue(),
                latestPrice.getChangeAmount().doubleValue(),
                latestPrice.getVolume(),
                0.0 // marketCap은 별도 계산 필요
        );
    }

    private StockDetailResponse.StockDetailData createStockDetailData(
            Stock stock,
            List<StockDetailResponse.ChartData> chartData,
            StockDetailResponse.SummaryData summaryData) {
        return new StockDetailResponse.StockDetailData(
                stock.getTicker(),
                stock.getName(),
                stock.getEngName(),
                stock.getExchange(),
                stock.getIndustryName(),
                chartData,
                summaryData
        );
    }
}
