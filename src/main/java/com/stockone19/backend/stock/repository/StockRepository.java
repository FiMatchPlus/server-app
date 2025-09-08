package com.stockone19.backend.stock.repository;

import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.domain.StockType;
import com.stockone19.backend.stock.dto.StockSearchResult;

import java.util.List;
import java.util.Optional;

public interface StockRepository {

    List<Stock> findByTickers(List<String> tickers);

    Optional<Stock> findByTicker(String ticker);

    List<Stock> findByType(StockType type);

    List<Stock> findByIndustryCode(Integer industryCode);

    List<Stock> findActiveStocks();

    /**
     * 종목 이름 또는 티커로 검색
     * @param keyword 검색 키워드 (종목명 또는 티커)
     * @param limit 검색 결과 제한 수
     * @return 검색된 종목 리스트
     */
    List<Stock> searchByNameOrTicker(String keyword, int limit);

    /**
     * 종목 이름 또는 티커로 검색 (가격 정보 포함)
     * @param keyword 검색 키워드 (종목명 또는 티커)
     * @param limit 검색 결과 제한 수
     * @return 검색된 종목 리스트 (가격 정보 포함)
     */
    List<StockSearchResult> searchByNameOrTickerWithPrice(String keyword, int limit);
}
