package com.stockone19.backend.portfolio.repository;

import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.PortfolioSnapshot;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.portfolio.domain.HoldingSnapshot;
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

    

    private static final RowMapper<Holding> HOLDING_ROW_MAPPER = (rs, rowNum) -> {
        java.math.BigDecimal changeAmount = rs.getBigDecimal("change_amount");
        java.math.BigDecimal changePercent = rs.getBigDecimal("change_percent");
        Double changeAmountVal = changeAmount != null ? changeAmount.doubleValue() : null;
        Double changePercentVal = changePercent != null ? changePercent.doubleValue() : null;

        return Holding.of(
                rs.getLong("id"),
                rs.getLong("portfolio_id"),
                rs.getString("symbol"),
                rs.getInt("shares"),
                rs.getDouble("current_price"),
                rs.getDouble("total_value"),
                changeAmountVal,
                changePercentVal,
                rs.getDouble("weight"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    };

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

    @Override
    public PortfolioSnapshot saveSnapshot(PortfolioSnapshot snapshot) {
        if (snapshot.id() == null) {
            return insertSnapshot(snapshot);
        } else {
            return updateSnapshot(snapshot);
        }
    }
    
    private PortfolioSnapshot insertSnapshot(PortfolioSnapshot snapshot) {
        String sql = """
            INSERT INTO portfolio_snapshots (portfolio_id, base_value, current_value, created_at, 
                                           metric_id, start_at, end_at, execution_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, snapshot.portfolioId());
            ps.setDouble(2, snapshot.baseValue());
            ps.setDouble(3, snapshot.currentValue());
            ps.setTimestamp(4, java.sql.Timestamp.valueOf(snapshot.createdAt()));
            ps.setString(5, snapshot.metricId());
            ps.setTimestamp(6, snapshot.startAt() != null ? java.sql.Timestamp.valueOf(snapshot.startAt()) : null);
            ps.setTimestamp(7, snapshot.endAt() != null ? java.sql.Timestamp.valueOf(snapshot.endAt()) : null);
            if (snapshot.executionTime() != null) {
                ps.setDouble(8, snapshot.executionTime());
            } else {
                ps.setNull(8, java.sql.Types.NUMERIC);
            }
            return ps;
        }, keyHolder);

        Long id = extractGeneratedId(keyHolder);
        return PortfolioSnapshot.of(
                id, snapshot.portfolioId(), snapshot.baseValue(), snapshot.currentValue(), 
                snapshot.createdAt(), snapshot.metricId(), snapshot.startAt(), snapshot.endAt(), 
                snapshot.executionTime()
        );
    }

    private PortfolioSnapshot updateSnapshot(PortfolioSnapshot snapshot) {
        String sql = """
            UPDATE portfolio_snapshots
            SET portfolio_id = ?, base_value = ?, current_value = ?, created_at = ?,
                metric_id = ?, start_at = ?, end_at = ?, execution_time = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
                snapshot.portfolioId(),
                snapshot.baseValue(),
                snapshot.currentValue(),
                java.sql.Timestamp.valueOf(snapshot.createdAt()),
                snapshot.metricId(),
                snapshot.startAt() != null ? java.sql.Timestamp.valueOf(snapshot.startAt()) : null,
                snapshot.endAt() != null ? java.sql.Timestamp.valueOf(snapshot.endAt()) : null,
                snapshot.executionTime(),
                snapshot.id()
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

    @Override
    public boolean existsSnapshotByPortfolioId(Long portfolioId) {
        String sql = """
            SELECT COUNT(*) FROM portfolio_snapshots 
            WHERE portfolio_id = ?
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, portfolioId);
        return count != null && count > 0;
    }

    @Override
    public List<PortfolioSnapshot> findSnapshotsByPortfolioId(Long portfolioId) {
        String sql = """
            SELECT id, portfolio_id, base_value, current_value, created_at, 
                   metric_id, start_at, end_at, execution_time
            FROM portfolio_snapshots
            WHERE portfolio_id = ?
            ORDER BY created_at ASC
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Double executionTime = rs.getObject("execution_time") != null ? 
                rs.getDouble("execution_time") : null;
            
            return PortfolioSnapshot.of(
                    rs.getLong("id"),
                    rs.getLong("portfolio_id"),
                    rs.getDouble("base_value"),
                    rs.getDouble("current_value"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getString("metric_id"),
                    rs.getTimestamp("start_at") != null ? rs.getTimestamp("start_at").toLocalDateTime() : null,
                    rs.getTimestamp("end_at") != null ? rs.getTimestamp("end_at").toLocalDateTime() : null,
                    executionTime
            );
        }, portfolioId);
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

    @Override
    public List<HoldingSnapshot> findHoldingSnapshotsByPortfolioSnapshotId(Long portfolioSnapshotId) {
        String sql = """
            SELECT id, recorded_at, price, quantity, value, weight, portfolio_snapshot_id, stock_code
            FROM holding_snapshots
            WHERE portfolio_snapshot_id = ?
            ORDER BY weight DESC
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> HoldingSnapshot.of(
                rs.getLong("id"),
                rs.getTimestamp("recorded_at").toLocalDateTime(),
                rs.getDouble("price"),
                rs.getInt("quantity"),
                rs.getDouble("value"),
                rs.getDouble("weight"),
                rs.getLong("portfolio_snapshot_id"),
                rs.getString("stock_code")
        ), portfolioSnapshotId);
    }

    @Override
    public HoldingSnapshot saveHoldingSnapshot(HoldingSnapshot holdingSnapshot) {
        String sql = """
            INSERT INTO holding_snapshots (portfolio_snapshot_id, stock_code, weight, price, quantity, value, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, holdingSnapshot.portfolioSnapshotId());
            ps.setString(2, holdingSnapshot.stockCode());
            ps.setDouble(3, holdingSnapshot.weight());
            ps.setDouble(4, holdingSnapshot.price());
            ps.setInt(5, holdingSnapshot.quantity());
            ps.setDouble(6, holdingSnapshot.value());
            ps.setTimestamp(7, java.sql.Timestamp.valueOf(holdingSnapshot.recordedAt()));
            return ps;
        }, keyHolder);

        Long id = extractGeneratedId(keyHolder);
        return HoldingSnapshot.of(
                id,
                holdingSnapshot.recordedAt(),
                holdingSnapshot.price(),
                holdingSnapshot.quantity(),
                holdingSnapshot.value(),
                holdingSnapshot.weight(),
                holdingSnapshot.portfolioSnapshotId(),
                holdingSnapshot.stockCode()
        );
    }
}













