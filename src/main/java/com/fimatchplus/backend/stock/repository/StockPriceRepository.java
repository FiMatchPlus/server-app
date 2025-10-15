package com.fimatchplus.backend.stock.repository;

import com.fimatchplus.backend.stock.domain.StockPrice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    /**
     * 특정 종목의 가격 히스토리를 조회합니다.
     */
    @Query(value = """
        SELECT sp FROM StockPrice sp 
        WHERE sp.stockCode = :stockCode 
        AND sp.intervalUnit = :intervalUnit 
        AND sp.datetime BETWEEN :startDate AND :endDate 
        ORDER BY sp.datetime DESC
        """)
    List<StockPrice> findByStockCodeAndInterval(
            @Param("stockCode") String stockCode,
            @Param("intervalUnit") String intervalUnit,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * 특정 종목의 최신 가격 데이터를 조회합니다.
     */
    StockPrice findFirstByStockCodeAndIntervalUnitOrderByDatetimeDesc(String stockCode, String intervalUnit);

    /**
     * 여러 종목의 최신 가격 데이터를 조회합니다. (성능 최적화된 네이티브 쿼리)
     */
    @Query(value = """
        SELECT DISTINCT ON (stock_code) 
            id, stock_code, datetime, interval_unit, 
            open_price, high_price, low_price, close_price, 
            volume, change_amount, change_rate
        FROM stock_prices 
        WHERE stock_code IN (:stockCodes) 
        AND interval_unit = :intervalUnit 
        ORDER BY stock_code, datetime DESC
        """, nativeQuery = true)
    List<StockPrice> findLatestByStockCodesAndInterval(
            @Param("stockCodes") List<String> stockCodes, 
            @Param("intervalUnit") String intervalUnit
    );

}

