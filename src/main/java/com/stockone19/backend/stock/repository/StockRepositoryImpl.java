package com.stockone19.backend.stock.repository;

import com.stockone19.backend.stock.domain.Stock;
import com.stockone19.backend.stock.domain.StockType;
import com.stockone19.backend.stock.dto.StockSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Stock> STOCK_ROW_MAPPER = (rs, rowNum) -> Stock.of(
            rs.getLong("id"),
            rs.getString("ticker"),
            rs.getString("name"),
            rs.getString("eng_name"),
            rs.getString("isin"),
            rs.getString("region"),
            rs.getString("currency"),
            rs.getString("major_code"),
            rs.getString("medium_code"),
            rs.getString("minor_code"),
            rs.getString("exchange"),
            rs.getBoolean("is_active"),
            rs.getObject("industry_code", Integer.class),
            rs.getString("industry_name"),
            StockType.valueOf(rs.getString("type"))
    );

    private static final RowMapper<StockSearchResult> STOCK_SEARCH_RESULT_ROW_MAPPER = (rs, rowNum) -> StockSearchResult.of(
            rs.getString("ticker"),
            rs.getString("name"),
            rs.getString("industry_name")
    );

    @Override
    public List<Stock> findByTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT id, ticker, name, eng_name, isin, region, currency,
                       major_code, medium_code, minor_code, exchange, is_active,
                       industry_code, industry_name, type
                FROM stocks
                WHERE ticker = ANY(?)
                """;

        return jdbcTemplate.query(sql, STOCK_ROW_MAPPER, tickers.toArray());
    }

    @Override
    public Optional<Stock> findByTicker(String ticker) {
        String sql = """
                SELECT id, ticker, name, eng_name, isin, region, currency,
                       major_code, medium_code, minor_code, exchange, is_active,
                       industry_code, industry_name, type
                FROM stocks
                WHERE ticker = ?
                """;

        List<Stock> results = jdbcTemplate.query(sql, STOCK_ROW_MAPPER, ticker);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<Stock> findByType(StockType type) {
        String sql = """
                SELECT id, ticker, name, eng_name, isin, region, currency,
                       major_code, medium_code, minor_code, exchange, is_active,
                       industry_code, industry_name, type
                FROM stocks
                WHERE type = ? AND is_active = true
                """;

        return jdbcTemplate.query(sql, STOCK_ROW_MAPPER, type.name());
    }

    @Override
    public List<Stock> findByIndustryCode(Integer industryCode) {
        String sql = """
                SELECT id, ticker, name, eng_name, isin, region, currency,
                       major_code, medium_code, minor_code, exchange, is_active,
                       industry_code, industry_name, type
                FROM stocks
                WHERE industry_code = ? AND is_active = true
                """;

        return jdbcTemplate.query(sql, STOCK_ROW_MAPPER, industryCode);
    }

    @Override
    public List<Stock> findActiveStocks() {
        String sql = """
                SELECT id, ticker, name, eng_name, isin, region, currency,
                       major_code, medium_code, minor_code, exchange, is_active,
                       industry_code, industry_name, type
                FROM stocks
                WHERE is_active = true
                ORDER BY ticker
                """;

        return jdbcTemplate.query(sql, STOCK_ROW_MAPPER);
    }

    @Override
    public List<Stock> searchByNameOrTicker(String keyword, int limit) {
        String sql = """
                SELECT id, ticker, name, eng_name, isin, region, currency,
                       major_code, medium_code, minor_code, exchange, is_active,
                       industry_code, industry_name, type
                FROM stocks
                WHERE is_active = true 
                AND (LOWER(ticker) LIKE LOWER(?) OR LOWER(name) LIKE LOWER(?) OR LOWER(eng_name) LIKE LOWER(?))
                ORDER BY 
                    CASE 
                        WHEN LOWER(ticker) = LOWER(?) THEN 1
                        WHEN LOWER(ticker) LIKE LOWER(?) THEN 2
                        WHEN LOWER(name) LIKE LOWER(?) THEN 3
                        WHEN LOWER(eng_name) LIKE LOWER(?) THEN 4
                        ELSE 5
                    END,
                    ticker
                LIMIT ?
                """;

        String searchPattern = "%" + keyword + "%";
        return jdbcTemplate.query(sql, STOCK_ROW_MAPPER,
                searchPattern, searchPattern, searchPattern,
                keyword, searchPattern, searchPattern, searchPattern,
                limit);
    }

    @Override
    public List<StockSearchResult> searchByNameOrTickerWithPrice(String keyword, int limit) {
        String sql = """
                SELECT s.ticker, s.name, s.industry_name
                FROM stocks s
                WHERE s.is_active = ?
                  AND (LOWER(s.ticker) LIKE LOWER(?)
                       OR LOWER(s.name) LIKE LOWER(?)
                       OR LOWER(s.eng_name) LIKE LOWER(?))
                ORDER BY
                    CASE
                        WHEN LOWER(s.ticker) = LOWER(?) THEN 1
                        WHEN LOWER(s.ticker) LIKE LOWER(?) THEN 2
                        WHEN LOWER(s.name) LIKE LOWER(?) THEN 3
                        WHEN LOWER(s.eng_name) LIKE LOWER(?) THEN 4
                        ELSE 5
                    END,
                    s.ticker
                LIMIT ?
         """;

        String searchPattern = "%" + keyword + "%";
        return jdbcTemplate.query(sql, STOCK_SEARCH_RESULT_ROW_MAPPER,
                "Y", searchPattern, searchPattern, searchPattern,
                keyword, searchPattern, searchPattern, searchPattern,
                limit);
    }
}
