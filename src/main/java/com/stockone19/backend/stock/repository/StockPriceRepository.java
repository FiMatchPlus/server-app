package com.stockone19.backend.stock.repository;

import com.stockone19.backend.stock.domain.StockPrice;

import java.time.LocalDateTime;
import java.util.List;

public interface StockPriceRepository {

    /**
     * 특정 종목의 가격 히스토리를 조회합니다.
     *
     * @param stockCode 종목 ID
     * @param intervalUnit 시간 간격 ('1m', '1d', '1W', '1Y')
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @param limit 조회할 레코드 수 제한
     * @return 주식 가격 히스토리 목록
     */
    List<StockPrice> findByStockIdAndInterval(
            String stockCode,
            String intervalUnit,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int limit
    );

    /**
     * 특정 종목의 최신 가격 데이터를 조회합니다.
     *
     * @param stockCode 종목 ID
     * @param intervalUnit 시간 간격
     * @return 최신 주식 가격 데이터
     */
    StockPrice findLatestByStockIdAndInterval(String stockCode, String intervalUnit);

    /**
     * 여러 종목의 최신 가격 데이터를 조회합니다.
     *
     * @param stockCodes 종목 ID 목록
     * @param intervalUnit 시간 간격
     * @return 주식 가격 히스토리 목록
     */
    List<StockPrice> findLatestByStockIdsAndInterval(List<String> stockCodes, String intervalUnit);

    /**
     * stock_prices 테이블에서 해당 ticker(stock_code)의 가장 최근 close_price 반환
     */
    java.math.BigDecimal findLatestClosePriceByTicker(String ticker);
}



