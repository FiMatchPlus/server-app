package com.fimatchplus.backend.backtest.repository;

import com.fimatchplus.backend.backtest.domain.ExecutionLog;
import com.fimatchplus.backend.backtest.domain.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ExecutionLog JDBC 배치 삽입 Repository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ExecutionLogJdbcRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
        INSERT INTO execution_logs 
        (backtest_id, log_date, action_type, category, trigger_value, threshold_value, reason, portfolio_value, sold_stocks, cash_generated, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    /**
     * ExecutionLog 배치 삽입
     */
    public int batchInsert(List<ExecutionLog> logs) {
        if (logs.isEmpty()) {
            return 0;
        }

        try {
            int[] batchResult = jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ExecutionLog executionLog = logs.get(i);
                    ps.setLong(1, executionLog.getBacktestId());
                    
                    // logDate null 체크
                    if (executionLog.getLogDate() != null) {
                        ps.setTimestamp(2, Timestamp.valueOf(executionLog.getLogDate()));
                    } else {
                        log.warn("logDate is null for execution log at index {}, using current time", i);
                        ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                    }
                    
                    ps.setString(3, executionLog.getActionType().name());
                    ps.setString(4, executionLog.getCategory());
                    ps.setDouble(5, executionLog.getTriggerValue() != null ? executionLog.getTriggerValue() : 0.0);
                    ps.setDouble(6, executionLog.getThresholdValue() != null ? executionLog.getThresholdValue() : 0.0);
                    ps.setString(7, executionLog.getReason());
                    ps.setDouble(8, executionLog.getPortfolioValue() != null ? executionLog.getPortfolioValue() : 0.0);
                    ps.setString(9, executionLog.getSoldStocks());
                    ps.setDouble(10, executionLog.getCashGenerated() != null ? executionLog.getCashGenerated() : 0.0);
                    
                    // createdAt null 체크
                    if (executionLog.getCreatedAt() != null) {
                        ps.setTimestamp(11, Timestamp.valueOf(executionLog.getCreatedAt()));
                    } else {
                        log.warn("createdAt is null for execution log at index {}, using current time", i);
                        ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
                    }
                }

                @Override
                public int getBatchSize() {
                    return logs.size();
                }
            });

            return batchResult.length;
        } catch (Exception e) {
            log.error("Failed to batch insert execution logs", e);
            throw new RuntimeException("Failed to batch insert execution logs", e);
        }
    }

    /**
     * 최적화된 배치 삽입 (큰 배치를 작은 단위로 분할)
     */
    public int optimizedBatchInsert(List<ExecutionLog> logs) {
        if (logs.isEmpty()) {
            return 0;
        }

        int batchSize = 1000;
        int totalInserted = 0;

        for (int i = 0; i < logs.size(); i += batchSize) {
            List<ExecutionLog> batch = logs.subList(i, Math.min(i + batchSize, logs.size()));
            totalInserted += batchInsert(batch);
        }

        return totalInserted;
    }

    /**
     * 백테스트 ID로 ExecutionLog 조회
     */
    public List<ExecutionLog> findByBacktestId(Long backtestId) {
        String SELECT_SQL = """
            SELECT id, backtest_id, log_date, action_type, category, 
                   trigger_value, threshold_value, reason, portfolio_value, sold_stocks, cash_generated, created_at
            FROM execution_logs 
            WHERE backtest_id = ? 
            ORDER BY log_date ASC
            """;

        try {
            return jdbcTemplate.query(SELECT_SQL, (rs, rowNum) -> {
                return ExecutionLog.builder()
                    .backtestId(rs.getLong("backtest_id"))
                    .logDate(rs.getTimestamp("log_date").toLocalDateTime())
                    .actionType(ActionType.valueOf(rs.getString("action_type")))
                    .category(rs.getString("category"))
                    .triggerValue(rs.getDouble("trigger_value"))
                    .thresholdValue(rs.getDouble("threshold_value"))
                    .reason(rs.getString("reason"))
                    .portfolioValue(rs.getDouble("portfolio_value"))
                    .soldStocks(rs.getString("sold_stocks"))
                    .cashGenerated(rs.getDouble("cash_generated"))
                    .build();
            }, backtestId);
        } catch (Exception e) {
            log.error("Failed to find execution logs by backtestId: {}", backtestId, e);
            return List.of();
        }
    }
}
