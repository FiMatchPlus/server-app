package com.fimatchplus.backend.backtest.repository;

import com.fimatchplus.backend.backtest.domain.BenchmarkPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 벤치마크 가격 데이터 조회 Repository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class BenchmarkPriceRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 벤치마크 코드와 기간으로 일별 데이터 조회
     * 
     * @param indexCode 벤치마크 코드
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 벤치마크 일별 데이터 목록
     */
    public List<BenchmarkPrice> findByIndexCodeAndDateRange(String indexCode, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = """
            SELECT id, index_code, datetime, open_price, high_price, low_price, 
                   close_price, change_amount, change_rate, volume, trading_value, 
                   market_cap, created_at, updated_at
            FROM benchmark_prices
            WHERE index_code = ? 
              AND datetime >= ? 
              AND datetime <= ?
            ORDER BY datetime ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            return BenchmarkPrice.of(
                    rs.getLong("id"),
                    rs.getString("index_code"),
                    rs.getTimestamp("datetime").toLocalDateTime(),
                    rs.getBigDecimal("open_price"),
                    rs.getBigDecimal("high_price"),
                    rs.getBigDecimal("low_price"),
                    rs.getBigDecimal("close_price"),
                    rs.getBigDecimal("change_amount"),
                    rs.getBigDecimal("change_rate"),
                    rs.getObject("volume") != null ? rs.getLong("volume") : null,
                    rs.getObject("trading_value") != null ? rs.getBigDecimal("trading_value") : null,
                    rs.getObject("market_cap") != null ? rs.getBigDecimal("market_cap") : null,
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                    rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null
            );
        }, indexCode, Timestamp.valueOf(startDate), Timestamp.valueOf(endDate));
    }
}
