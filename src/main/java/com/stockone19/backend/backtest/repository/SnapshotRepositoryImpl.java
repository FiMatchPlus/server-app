package com.stockone19.backend.backtest.repository;

import com.stockone19.backend.backtest.domain.HoldingSnapshot;
import com.stockone19.backend.backtest.domain.PortfolioSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.postgresql.util.PGobject;

@Repository
@RequiredArgsConstructor
public class SnapshotRepositoryImpl implements SnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public PortfolioSnapshot savePortfolioSnapshot(PortfolioSnapshot snapshot) {
        if (snapshot.id() == null) {
            return insertPortfolioSnapshot(snapshot);
        } else {
            return updatePortfolioSnapshot(snapshot);
        }
    }
    
    private PortfolioSnapshot insertPortfolioSnapshot(PortfolioSnapshot snapshot) {
        String sql = """
            INSERT INTO portfolio_snapshots (backtest_id, base_value, current_value, created_at, 
                                           metrics, start_at, end_at, execution_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, snapshot.backtestId());
            ps.setDouble(2, snapshot.baseValue());
            ps.setDouble(3, snapshot.currentValue());
            ps.setTimestamp(4, Timestamp.valueOf(snapshot.createdAt()));
            if (snapshot.metrics() != null) {
                PGobject jsonbObject = new PGobject();
                jsonbObject.setType("jsonb");
                jsonbObject.setValue(snapshot.metrics());
                ps.setObject(5, jsonbObject);
            } else {
                ps.setNull(5, Types.OTHER);
            }
            ps.setTimestamp(6, snapshot.startAt() != null ? Timestamp.valueOf(snapshot.startAt()) : null);
            ps.setTimestamp(7, snapshot.endAt() != null ? Timestamp.valueOf(snapshot.endAt()) : null);
            if (snapshot.executionTime() != null) {
                ps.setDouble(8, snapshot.executionTime());
            } else {
                ps.setNull(8, Types.NUMERIC);
            }
            return ps;
        }, keyHolder);

        Long id = extractGeneratedId(keyHolder);
        return PortfolioSnapshot.of(
                id, snapshot.backtestId(), snapshot.baseValue(), snapshot.currentValue(), 
                snapshot.createdAt(), snapshot.metrics(), snapshot.startAt(), snapshot.endAt(), 
                snapshot.executionTime()
        );
    }

    private PortfolioSnapshot updatePortfolioSnapshot(PortfolioSnapshot snapshot) {
        String sql = """
            UPDATE portfolio_snapshots
            SET backtest_id = ?, base_value = ?, current_value = ?, created_at = ?,
                metrics = ?, start_at = ?, end_at = ?, execution_time = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, snapshot.backtestId());
            ps.setDouble(2, snapshot.baseValue());
            ps.setDouble(3, snapshot.currentValue());
            ps.setTimestamp(4, Timestamp.valueOf(snapshot.createdAt()));
            if (snapshot.metrics() != null) {
                PGobject jsonbObject = new PGobject();
                jsonbObject.setType("jsonb");
                jsonbObject.setValue(snapshot.metrics());
                ps.setObject(5, jsonbObject);
            } else {
                ps.setNull(5, Types.OTHER);
            }
            ps.setTimestamp(6, snapshot.startAt() != null ? Timestamp.valueOf(snapshot.startAt()) : null);
            ps.setTimestamp(7, snapshot.endAt() != null ? Timestamp.valueOf(snapshot.endAt()) : null);
            if (snapshot.executionTime() != null) {
                ps.setDouble(8, snapshot.executionTime());
            } else {
                ps.setNull(8, Types.NUMERIC);
            }
            ps.setLong(9, snapshot.id());
            return ps;
        });

        return snapshot;
    }

    @Override
    public boolean existsPortfolioSnapshotByBacktestId(Long backtestId) {
        String sql = """
            SELECT COUNT(*) FROM portfolio_snapshots 
            WHERE backtest_id = ?
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, backtestId);
        return count != null && count > 0;
    }

    @Override
    public List<PortfolioSnapshot> findPortfolioSnapshotsByBacktestId(Long backtestId) {
        String sql = """
            SELECT id, backtest_id, base_value, current_value, created_at, 
                   metrics, start_at, end_at, execution_time
            FROM portfolio_snapshots
            WHERE backtest_id = ?
            ORDER BY created_at ASC
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Double executionTime = rs.getObject("execution_time") != null ? 
                rs.getDouble("execution_time") : null;
            
            return PortfolioSnapshot.of(
                    rs.getLong("id"),
                    rs.getLong("backtest_id"),
                    rs.getDouble("base_value"),
                    rs.getDouble("current_value"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getString("metrics"),
                    rs.getTimestamp("start_at") != null ? rs.getTimestamp("start_at").toLocalDateTime() : null,
                    rs.getTimestamp("end_at") != null ? rs.getTimestamp("end_at").toLocalDateTime() : null,
                    executionTime
            );
        }, backtestId);
    }

    @Override
    public PortfolioSnapshot findLatestPortfolioSnapshotByBacktestId(Long backtestId) {
        String sql = """
            SELECT id, backtest_id, base_value, current_value, created_at, 
                   metrics, start_at, end_at, execution_time
            FROM portfolio_snapshots
            WHERE backtest_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;
        
        List<PortfolioSnapshot> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Double executionTime = rs.getObject("execution_time") != null ? 
                rs.getDouble("execution_time") : null;
            
            return PortfolioSnapshot.of(
                    rs.getLong("id"),
                    rs.getLong("backtest_id"),
                    rs.getDouble("base_value"),
                    rs.getDouble("current_value"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getString("metrics"),
                    rs.getTimestamp("start_at") != null ? rs.getTimestamp("start_at").toLocalDateTime() : null,
                    rs.getTimestamp("end_at") != null ? rs.getTimestamp("end_at").toLocalDateTime() : null,
                    executionTime
            );
        }, backtestId);
        
        if (results.isEmpty()) {
            throw new RuntimeException("해당 백테스트에 대한 포트폴리오 스냅샷을 찾을 수 없습니다. backtestId: " + backtestId);
        }
        
        return results.get(0);
    }

    @Override
    public HoldingSnapshot saveHoldingSnapshot(HoldingSnapshot holdingSnapshot) {
        String sql = """
            INSERT INTO holding_snapshots (portfolio_snapshot_id, stock_code, weight, price, quantity, value, recorded_at, contribution, daily_ratio)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            if (holdingSnapshot.portfolioSnapshotId() != null) {
                ps.setLong(1, holdingSnapshot.portfolioSnapshotId());
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setString(2, holdingSnapshot.stockCode());
            ps.setDouble(3, holdingSnapshot.weight());
            ps.setDouble(4, holdingSnapshot.price());
            ps.setInt(5, holdingSnapshot.quantity());
            ps.setDouble(6, holdingSnapshot.value());
            ps.setTimestamp(7, Timestamp.valueOf(holdingSnapshot.recordedAt()));
            ps.setDouble(8, holdingSnapshot.contribution());
            ps.setDouble(9, holdingSnapshot.dailyRatio());
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
                holdingSnapshot.stockCode(),
                holdingSnapshot.contribution(),
                holdingSnapshot.dailyRatio()
        );
    }

    @Override
    public int saveHoldingSnapshotsBatch(List<HoldingSnapshot> holdingSnapshots) {
        if (holdingSnapshots == null || holdingSnapshots.isEmpty()) {
            return 0;
        }

        String sql = """
            INSERT INTO holding_snapshots (portfolio_snapshot_id, stock_code, weight, price, quantity, value, recorded_at, contribution, daily_ratio)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        List<Object[]> batchArgs = holdingSnapshots.stream()
                .map(snapshot -> new Object[]{
                        snapshot.portfolioSnapshotId(),
                        snapshot.stockCode(),
                        snapshot.weight(),
                        snapshot.price(),
                        snapshot.quantity(),
                        snapshot.value(),
                        Timestamp.valueOf(snapshot.recordedAt()),
                        snapshot.contribution(),
                        snapshot.dailyRatio()
                })
                .collect(Collectors.toList());

        int[] results = jdbcTemplate.batchUpdate(sql, batchArgs);
        return Arrays.stream(results).sum();
    }

    @Override
    public List<HoldingSnapshot> findHoldingSnapshotsByPortfolioSnapshotId(Long portfolioSnapshotId) {
        String sql = """
            SELECT id, recorded_at, price, quantity, value, weight, portfolio_snapshot_id, stock_code, contribution, daily_ratio
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
                rs.getString("stock_code"),
                rs.getDouble("contribution"),
                rs.getDouble("daily_ratio")
        ), portfolioSnapshotId);
    }

    @Override
    public List<HoldingSnapshot> findHoldingSnapshotsByBacktestId(Long backtestId) {
        String sql = """
            SELECT hs.id, hs.recorded_at, hs.price, hs.quantity, hs.value, hs.weight, 
                   hs.portfolio_snapshot_id, hs.stock_code, hs.contribution, hs.daily_ratio
            FROM holding_snapshots hs
            INNER JOIN portfolio_snapshots ps ON hs.portfolio_snapshot_id = ps.id
            WHERE ps.backtest_id = ?
            ORDER BY ps.created_at ASC, hs.weight DESC
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> HoldingSnapshot.of(
                rs.getLong("id"),
                rs.getTimestamp("recorded_at").toLocalDateTime(),
                rs.getDouble("price"),
                rs.getInt("quantity"),
                rs.getDouble("value"),
                rs.getDouble("weight"),
                rs.getLong("portfolio_snapshot_id"),
                rs.getString("stock_code"),
                rs.getDouble("contribution"),
                rs.getDouble("daily_ratio")
        ), backtestId);
    }

    private static Long extractGeneratedId(KeyHolder keyHolder) {
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

        if (!keyHolder.getKeyList().isEmpty()) {
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
    public int deleteHoldingSnapshotsByPortfolioSnapshotId(Long portfolioSnapshotId) {
        String sql = "DELETE FROM holding_snapshots WHERE portfolio_snapshot_id = ?";
        return jdbcTemplate.update(sql, portfolioSnapshotId);
    }

    @Override
    public int deletePortfolioSnapshotById(Long portfolioSnapshotId) {
        String sql = "DELETE FROM portfolio_snapshots WHERE id = ?";
        return jdbcTemplate.update(sql, portfolioSnapshotId);
    }

    @Override
    public PortfolioSnapshot findById(Long id) {
        String sql = """
            SELECT id, backtest_id, base_value, current_value, created_at, 
                   metrics, start_at, end_at, execution_time
            FROM portfolio_snapshots 
            WHERE id = ?
            """;
        
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Long backtestId = rs.getLong("backtest_id");
                Double baseValue = rs.getDouble("base_value");
                Double currentValue = rs.getDouble("current_value");
                Timestamp createdAt = rs.getTimestamp("created_at");
                String metrics = rs.getString("metrics");
                Timestamp startAt = rs.getTimestamp("start_at");
                Timestamp endAt = rs.getTimestamp("end_at");
                Double executionTime = rs.getDouble("execution_time");
                
                return new PortfolioSnapshot(
                    rs.getLong("id"),
                    backtestId,
                    baseValue,
                    currentValue,
                    createdAt.toLocalDateTime(),
                    metrics,
                    startAt.toLocalDateTime(),
                    endAt.toLocalDateTime(),
                    executionTime
                );
            }, id);
        } catch (Exception e) {
            return null;
        }
    }
}
