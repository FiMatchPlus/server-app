package com.stockone19.backend.stock.repository;

import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.domain.StockType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findByTickerIn(List<String> tickers);

    Optional<Stock> findByTicker(String ticker);

    List<Stock> findByType(StockType type);

    List<Stock> findByIndustryCode(Integer industryCode);

    List<Stock> findByIsActiveTrue();

    /**
     * 종목 이름 또는 티커로 검색 (기본 정보만)
     * @param keyword 검색 키워드 (종목명 또는 티커)
     * @param pageable 페이징 정보 (limit 포함)
     * @return 검색된 종목 리스트
     */
    @Query(value = """
        SELECT s FROM Stock s 
        WHERE s.isActive = true 
        AND (LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
             OR LOWER(s.ticker) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY 
            CASE WHEN LOWER(s.ticker) = LOWER(:keyword) THEN 1
                 WHEN LOWER(s.name) = LOWER(:keyword) THEN 2
                 WHEN LOWER(s.ticker) LIKE LOWER(CONCAT(:keyword, '%')) THEN 3
                 WHEN LOWER(s.name) LIKE LOWER(CONCAT(:keyword, '%')) THEN 4
                 ELSE 5 END,
            s.name
        """)
    List<Stock> searchByNameOrTicker(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);

}
