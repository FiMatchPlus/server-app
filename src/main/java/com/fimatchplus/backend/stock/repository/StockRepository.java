package com.fimatchplus.backend.stock.repository;

import com.fimatchplus.backend.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findByTickerIn(List<String> tickers);

    Optional<Stock> findByTicker(String ticker);

    /**
     * 종목 이름, 영문명 또는 티커로 검색 (기본 정보만)
     * @param keyword 검색 키워드 (종목명, 영문명 또는 티커)
     * @param pageable 페이징 정보 (limit 포함)
     * @return 검색된 종목 리스트
     */
    @Query(value = """
        SELECT s FROM Stock s 
        WHERE s.isActive = true 
        AND (LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
             OR LOWER(s.ticker) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(s.engName) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY 
            CASE WHEN LOWER(s.ticker) = LOWER(:keyword) THEN 1
                 WHEN LOWER(s.name) = LOWER(:keyword) THEN 2
                 WHEN LOWER(s.engName) = LOWER(:keyword) THEN 3
                 WHEN LOWER(s.ticker) LIKE LOWER(CONCAT(:keyword, '%')) THEN 4
                 WHEN LOWER(s.name) LIKE LOWER(CONCAT(:keyword, '%')) THEN 5
                 WHEN LOWER(s.engName) LIKE LOWER(CONCAT(:keyword, '%')) THEN 6
                 ELSE 7 END,
            s.name
        """)
    List<Stock> searchByNameOrTicker(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);

}
