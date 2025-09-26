package com.stockone19.backend.benchmark.repository;

import com.stockone19.backend.benchmark.domain.BenchmarkPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 벤치마크 지수 가격 정보 리포지토리
 */
public interface BenchmarkPriceRepository extends JpaRepository<BenchmarkPrice, Long> {

    /**
     * 특정 벤치마크 지수의 기간별 가격 데이터 조회
     * @param indexCode 벤치마크 지수 코드 (KOSPI, KOSDAQ 등)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 해당 기간의 벤치마크 가격 데이터 리스트
     */
    @Query(value = """
        SELECT bp FROM BenchmarkPrice bp 
        WHERE bp.indexCode = :indexCode 
        AND bp.datetime BETWEEN :startDate AND :endDate 
        ORDER BY bp.datetime ASC
        """)
    List<BenchmarkPrice> findByIndexCodeAndDateRange(
            @Param("indexCode") String indexCode,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 특정 벤치마크 지수의 최신 가격 데이터 조회
     * @param indexCode 벤치마크 지수 코드
     * @return 최신 벤치마크 가격 데이터
     */
    BenchmarkPrice findFirstByIndexCodeOrderByDatetimeDesc(String indexCode);

    /**
     * 여러 벤치마크 지수의 최신 가격 데이터 조회
     * @param indexCodes 벤치마크 지수 코드 리스트
     * @return 각 지수의 최신 가격 데이터 리스트
     */
    @Query(value = """
        SELECT DISTINCT ON (index_code) 
            id, index_code, datetime, open_price, high_price, low_price, close_price, 
            change_amount, change_rate, volume, trading_value, market_cap, created_at, updated_at
        FROM benchmark_prices 
        WHERE index_code IN (:indexCodes) 
        ORDER BY index_code, datetime DESC
        """)
    List<BenchmarkPrice> findLatestByIndexCodes(@Param("indexCodes") List<String> indexCodes);

    /**
     * 특정 벤치마크 지수의 특정 날짜 가격 데이터 조회
     * @param indexCode 벤치마크 지수 코드
     * @param date 조회할 날짜
     * @return 해당 날짜의 벤치마크 가격 데이터
     */
    BenchmarkPrice findByIndexCodeAndDatetime(String indexCode, LocalDateTime date);
}
