package com.stockone19.backend.stock.repository;

import com.stockone19.backend.stock.domain.StockPrice;
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
        WHERE sp.stockId = :stockCode 
        AND sp.intervalUnit = :intervalUnit 
        AND sp.datetime BETWEEN :startDate AND :endDate 
        ORDER BY sp.datetime DESC
        """)
    List<StockPrice> findByStockIdAndInterval(
            @Param("stockCode") String stockCode,
            @Param("intervalUnit") String intervalUnit,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("limit") int limit
    );

    /**
     * 특정 종목의 최신 가격 데이터를 조회합니다.
     */
    StockPrice findFirstByStockIdAndIntervalUnitOrderByDatetimeDesc(String stockCode, String intervalUnit);

    /**
     * 여러 종목의 최신 가격 데이터를 조회합니다.
     */
    @Query(value = """
        SELECT sp FROM StockPrice sp 
        WHERE sp.stockId IN :stockCodes 
        AND sp.intervalUnit = :intervalUnit 
        AND sp.datetime = (
            SELECT MAX(sp2.datetime) 
            FROM StockPrice sp2 
            WHERE sp2.stockId = sp.stockId 
            AND sp2.intervalUnit = :intervalUnit
        )
        """)
    List<StockPrice> findLatestByStockIdsAndInterval(
            @Param("stockCodes") List<String> stockCodes, 
            @Param("intervalUnit") String intervalUnit
    );

}

