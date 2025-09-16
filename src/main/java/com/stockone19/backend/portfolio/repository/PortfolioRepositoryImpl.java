package com.stockone19.backend.portfolio.repository;

import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.PortfolioSnapshot;
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
import java.util.Objects;
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

    private static final RowMapper<PortfolioSnapshot> SNAPSHOT_ROW_MAPPER = (rs, rowNum) -> PortfolioSnapshot.of(
            rs.getLong("id"),
            rs.getTimestamp("recorded_at").toLocalDateTime(),
            rs.getDouble("base_value"),
            rs.getDouble("current_value"),
            rs.getLong("portfolio_id")
    );

    private static final RowMapper<HoldingSnapshot> HOLDING_ROW_MAPPER = (rs, rowNum) -> HoldingSnapshot.of(
            rs.getLong("id"),
            rs.getTimestamp("recorded_at").toLocalDateTime(),
            rs.getDouble("price"),
            rs.getInt("quantity"),
            rs.getDouble("value"),
            rs.getDouble("weight"),
            rs.getLong("portfolio_snapshot_id"),
            rs.getString("stock_id")
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

        Long id = keyHolder.getKey().longValue();
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
    public List<PortfolioSnapshot> findSnapshotsByPortfolioId(Long portfolioId) {
        String sql = """
            SELECT id, recorded_at, base_value, current_value, portfolio_id
            FROM portfolio_snapshots
            WHERE portfolio_id = ?
            ORDER BY recorded_at DESC
            """;

        return jdbcTemplate.query(sql, SNAPSHOT_ROW_MAPPER, portfolioId);
    }

    @Override
    public Optional<PortfolioSnapshot> findLatestSnapshotByPortfolioId(Long portfolioId) {
        String sql = """
            SELECT id, recorded_at, base_value, current_value, portfolio_id
            FROM portfolio_snapshots
            WHERE portfolio_id = ?
            ORDER BY recorded_at DESC
            LIMIT 1
            """;

        List<PortfolioSnapshot> results = jdbcTemplate.query(sql, SNAPSHOT_ROW_MAPPER, portfolioId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
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

        Long id = keyHolder.getKey().longValue();
        return PortfolioSnapshot.of(
                id, snapshot.recordedAt(), snapshot.baseValue(), snapshot.currentValue(), snapshot.portfolioId()
        );
    }

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

    @Override
    public List<HoldingSnapshot> findHoldingsBySnapshotId(Long snapshotId) {
        String sql = """
            SELECT id, recorded_at, price, quantity, value, weight, portfolio_snapshot_id, stock_id
            FROM holding_snapshots
            WHERE portfolio_snapshot_id = ?
            ORDER BY weight DESC
            """;

        return jdbcTemplate.query(sql, HOLDING_ROW_MAPPER, snapshotId);
    }

    @Override
    public HoldingSnapshot saveHolding(HoldingSnapshot holding) {
        if (holding.id() == null) {
            return insertHolding(holding);
        } else {
            return updateHolding(holding);
        }
    }

    private HoldingSnapshot insertHolding(HoldingSnapshot holding) {
        String sql = """
            INSERT INTO holding_snapshots (recorded_at, price, quantity, value, weight, portfolio_snapshot_id, stock_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(holding.recordedAt()));
            ps.setDouble(2, holding.price());
            ps.setInt(3, holding.quantity());
            ps.setDouble(4, holding.value());
            ps.setDouble(5, holding.weight());
            ps.setLong(6, holding.portfolioSnapshotId());
            ps.setString(7, holding.stockCode());
            return ps;
        }, keyHolder);

        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return HoldingSnapshot.of(
                id, holding.recordedAt(), holding.price(), holding.quantity(),
                holding.value(), holding.weight(), holding.portfolioSnapshotId(), holding.stockCode()
        );
    }

    private HoldingSnapshot updateHolding(HoldingSnapshot holding) {
        String sql = """
            UPDATE holding_snapshots
            SET recorded_at = ?, price = ?, quantity = ?, value = ?, weight = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
                java.sql.Timestamp.valueOf(holding.recordedAt()),
                holding.price(), holding.quantity(), holding.value(), holding.weight(), holding.id()
        );

        return holding;
    }
}













