package com.stockone19.backend.portfolio.repository;

import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.PortfolioSnapshot;
import com.stockone19.backend.portfolio.domain.Holding;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PortfolioRepositoryImpl implements PortfolioRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Portfolio> PORTFOLIO_ROW_MAPPER = (rs, rowNum) -> Portfolio.of(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("rule_id"),
            "Y".equals(rs.getString("is_main")),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(),
            rs.getLong("user_id")
    );

    

    private static final RowMapper<Holding> HOLDING_ROW_MAPPER = (rs, rowNum) -> Holding.of(
            rs.getLong("id"),
            rs.getLong("portfolio_id"),
            rs.getString("symbol"),
            rs.getInt("shares"),
            rs.getDouble("current_price"),
            rs.getDouble("total_value"),
            (Double) rs.getObject("change_amount"),
            (Double) rs.getObject("change_percent"),
            rs.getDouble("weight"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    @Override
    public List<Portfolio> findByUserId(Long userId) {
        String sql = """
            SELECT id, name, description, rule_id, is_main, created_at, updated_at, user_id
            FROM portfolios
            WHERE user_id = ?
            ORDER BY CASE WHEN is_main = 'Y' THEN 0 ELSE 1 END, created_at DESC
            """;

        return jdbcTemplate.query(sql, PORTFOLIO_ROW_MAPPER, userId);
    }

    @Override
    public Optional<Portfolio> findById(Long portfolioId) {
        String sql = """
            SELECT id, name, description, rule_id, is_main, created_at, updated_at, user_id
            FROM portfolios
            WHERE id = ?
            """;

        List<Portfolio> results = jdbcTemplate.query(sql, PORTFOLIO_ROW_MAPPER, portfolioId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<Portfolio> findMainPortfolioByUserId(Long userId) {
        String sql = """
            SELECT id, name, description, rule_id, is_main, created_at, updated_at, user_id
            FROM portfolios
            WHERE user_id = ? AND is_main = 'Y'
            """;

        List<Portfolio> results = jdbcTemplate.query(sql, PORTFOLIO_ROW_MAPPER, userId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Portfolio save(Portfolio portfolio) {
        if (portfolio.id() == null) {
            return insert(portfolio);
        } else {
            return update(portfolio);
        }
    }

    private Portfolio insert(Portfolio portfolio) {
        String sql = """
            INSERT INTO portfolios (name, description, rule_id, is_main, created_at, updated_at, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, portfolio.name());
            ps.setString(2, portfolio.description());
            ps.setString(3, portfolio.ruleId());
            ps.setString(4, portfolio.isMain() ? "Y" : "N");
            ps.setTimestamp(5, java.sql.Timestamp.valueOf(portfolio.createdAt()));
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(portfolio.updatedAt()));
            ps.setLong(7, portfolio.userId());
            return ps;
        }, keyHolder);

        Long id = extractGeneratedId(keyHolder);
        return Portfolio.of(
                id, portfolio.name(), portfolio.description(), portfolio.ruleId(),
                portfolio.isMain(), portfolio.createdAt(), portfolio.updatedAt(), portfolio.userId()
        );
    }

    private Portfolio update(Portfolio portfolio) {
        String sql = """
            UPDATE portfolios
            SET name = ?, description = ?, rule_id = ?, is_main = ?, updated_at = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
                portfolio.name(), portfolio.description(), portfolio.ruleId(),
                portfolio.isMain() ? "Y" : "N", java.sql.Timestamp.valueOf(portfolio.updatedAt()), portfolio.id()
        );

        return portfolio;
    }

    // snapshots are not used in current implementation
    @SuppressWarnings("unused")
    private PortfolioSnapshot insertSnapshot(PortfolioSnapshot snapshot) {
        String sql = """
            INSERT INTO portfolio_snapshots (recorded_at, base_value, current_value, portfolio_id)
            VALUES (?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(snapshot.recordedAt()));
            ps.setDouble(2, snapshot.baseValue());
            ps.setDouble(3, snapshot.currentValue());
            ps.setLong(4, snapshot.portfolioId());
            return ps;
        }, keyHolder);

        Long id = extractGeneratedId(keyHolder);
        return PortfolioSnapshot.of(
                id, snapshot.recordedAt(), snapshot.baseValue(), snapshot.currentValue(), snapshot.portfolioId()
        );
    }

    @SuppressWarnings("unused")
    private PortfolioSnapshot updateSnapshot(PortfolioSnapshot snapshot) {
        String sql = """
            UPDATE portfolio_snapshots
            SET recorded_at = ?, base_value = ?, current_value = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
                java.sql.Timestamp.valueOf(snapshot.recordedAt()),
                snapshot.baseValue(), snapshot.currentValue(), snapshot.id()
        );

        return snapshot;
    }

    public Holding saveHolding(Holding holding) {
        String sql = """
            INSERT INTO holdings (portfolio_id, symbol, shares, current_price, total_value, change_amount, change_percent, weight, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, holding.portfolioId());
            ps.setString(2, holding.symbol());
            ps.setInt(3, holding.shares());
            ps.setDouble(4, holding.currentPrice());
            ps.setDouble(5, holding.totalValue());
            if (holding.changeAmount() == null) ps.setNull(6, java.sql.Types.NUMERIC); else ps.setDouble(6, holding.changeAmount());
            if (holding.changePercent() == null) ps.setNull(7, java.sql.Types.NUMERIC); else ps.setDouble(7, holding.changePercent());
            ps.setDouble(8, holding.weight());
            ps.setTimestamp(9, java.sql.Timestamp.valueOf(holding.createdAt()));
            ps.setTimestamp(10, java.sql.Timestamp.valueOf(holding.updatedAt()));
            return ps;
        }, keyHolder);

        Long id = extractGeneratedId(keyHolder);
        return Holding.of(
                id, holding.portfolioId(), holding.symbol(), holding.shares(), holding.currentPrice(), holding.totalValue(),
                holding.changeAmount(), holding.changePercent(), holding.weight(), holding.createdAt(), holding.updatedAt()
        );
    }

    public List<Holding> findHoldingsByPortfolioId(Long portfolioId) {
        String sql = """
            SELECT id, portfolio_id, symbol, shares, current_price, total_value, change_amount, change_percent, weight, created_at, updated_at
            FROM holdings
            WHERE portfolio_id = ?
            ORDER BY weight DESC
            """;
        return jdbcTemplate.query(sql, HOLDING_ROW_MAPPER, portfolioId);
    }

    

    private static Long extractGeneratedId(KeyHolder keyHolder) {
        if (keyHolder == null) {
            throw new IllegalStateException("KeyHolder is null");
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object idObj = null;
            if (keys.containsKey("id")) {
                idObj = keys.get("id");
            } else if (keys.containsKey("ID")) {
                idObj = keys.get("ID");
            } else if (keys.containsKey("Id")) {
                idObj = keys.get("Id");
            } else {
                for (Object value : keys.values()) {
                    if (value instanceof Number) {
                        idObj = value;
                        break;
                    }
                }
            }

            if (idObj instanceof Number) {
                return ((Number) idObj).longValue();
            }
        }

        Number singleKey = keyHolder.getKey();
        if (singleKey != null) {
            return singleKey.longValue();
        }

        if (keyHolder.getKeyList() != null && !keyHolder.getKeyList().isEmpty()) {
            Map<String, Object> first = keyHolder.getKeyList().get(0);
            Object idObj = first.get("id");
            if (idObj == null) idObj = first.get("ID");
            if (idObj == null) idObj = first.get("Id");
            if (idObj instanceof Number) {
                return ((Number) idObj).longValue();
            }
            for (Object value : first.values()) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
            }
        }

        throw new IllegalStateException("Could not retrieve generated id from KeyHolder");
    }

    
}













