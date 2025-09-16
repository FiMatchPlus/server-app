package com.stockone19.backend.stock.repository;

import com.stockone19.backend.stock.domain.StockPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StockPriceRepositoryImpl implements StockPriceRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<StockPrice> STOCK_PRICE_ROW_MAPPER = (rs, rowNum) ->
            StockPrice.of(
                    rs.getLong("id"),
                    rs.getString("stock_code"),
                    rs.getTimestamp("datetime").toLocalDateTime(),
                    rs.getString("interval_unit"),
                    rs.getBigDecimal("open_price"),
                    rs.getBigDecimal("high_price"),
                    rs.getBigDecimal("low_price"),
                    rs.getBigDecimal("close_price"),
                    rs.getLong("volume"),
                    rs.getBigDecimal("change_amount"),
                    rs.getBigDecimal("change_rate")
            );

    @Override
    public List<StockPrice> findByStockIdAndInterval(
            String stockCode,
            String intervalUnit,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int limit) {

        String sql = """
            SELECT id, stock_code, datetime, interval_unit,
                   open_price, high_price, low_price, close_price, volume,
                   change_amount, change_rate
            FROM stock_prices
            WHERE stock_code = ? 
              AND interval_unit = ?
              AND datetime BETWEEN ? AND ?
            ORDER BY datetime
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                STOCK_PRICE_ROW_MAPPER,
                stockCode, intervalUnit, startDate, endDate, limit
        );
    }

    @Override
    public StockPrice findLatestByStockIdAndInterval(String stockCode, String intervalUnit) {
        String sql = """
            SELECT id, stock_code, datetime, interval_unit,
                   open_price, high_price, low_price, close_price, volume,
                   change_amount, change_rate
            FROM stock_prices
            WHERE stock_code = ? AND interval_unit = ?
            ORDER BY datetime
            LIMIT 1
            """;

        List<StockPrice> results = jdbcTemplate.query(
                sql,
                STOCK_PRICE_ROW_MAPPER,
                stockCode, intervalUnit
        );

        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<StockPrice> findLatestByStockIdsAndInterval(List<String> stockCodes, String intervalUnit) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return List.of();
        }

        String sql = """
            SELECT DISTINCT ON (stock_code) 
                   id, stock_code, datetime, interval_unit,
                   open_price, high_price, low_price, close_price, volume,
                   change_amount, change_rate
            FROM stock_prices
            WHERE stock_code = ANY(?) AND interval_unit = ?
            ORDER BY stock_code, datetime
            """;

        return jdbcTemplate.query(
                sql,
                STOCK_PRICE_ROW_MAPPER,
                stockCodes.toArray(), intervalUnit
        );
    }

    @Override
    public java.math.BigDecimal findLatestClosePriceByTicker(String ticker) {
        String sql = """
            SELECT close_price
            FROM stock_prices
            WHERE stock_code = ?
            ORDER BY datetime DESC
            LIMIT 1
            """;

        List<java.math.BigDecimal> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getBigDecimal("close_price"),
                ticker
        );

        return results.isEmpty() ? java.math.BigDecimal.ZERO : results.get(0);
    }
}


