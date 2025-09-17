package com.stockone19.backend.backtest.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * MongoDB에 저장되는 백테스트 성과 지표 문서
 */
@Document(collection = "metrics")
public class BacktestMetricsDocument {

    @Id
    private String id;

    @Field("portfolio_snapshot_id")
    private Long portfolioSnapshotId;

    @Field("total_return")
    private double totalReturn;

    @Field("annualized_return")
    private double annualizedReturn;

    @Field("volatility")
    private double volatility;

    @Field("sharpe_ratio")
    private double sharpeRatio;

    @Field("max_drawdown")
    private double maxDrawdown;

    @Field("var_95")
    private double var95;

    @Field("var_99")
    private double var99;

    @Field("cvar_95")
    private double cvar95;

    @Field("cvar_99")
    private double cvar99;

    @Field("win_rate")
    private double winRate;

    @Field("profit_loss_ratio")
    private double profitLossRatio;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    // 기본 생성자
    public BacktestMetricsDocument() {}

    // 생성자
    public BacktestMetricsDocument(Long portfolioSnapshotId, double totalReturn, double annualizedReturn,
                                   double volatility, double sharpeRatio, double maxDrawdown,
                                   double var95, double var99, double cvar95, double cvar99,
                                   double winRate, double profitLossRatio) {
        this.portfolioSnapshotId = portfolioSnapshotId;
        this.totalReturn = totalReturn;
        this.annualizedReturn = annualizedReturn;
        this.volatility = volatility;
        this.sharpeRatio = sharpeRatio;
        this.maxDrawdown = maxDrawdown;
        this.var95 = var95;
        this.var99 = var99;
        this.cvar95 = cvar95;
        this.cvar99 = cvar99;
        this.winRate = winRate;
        this.profitLossRatio = profitLossRatio;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getPortfolioSnapshotId() { return portfolioSnapshotId; }
    public void setPortfolioSnapshotId(Long portfolioSnapshotId) { this.portfolioSnapshotId = portfolioSnapshotId; }

    public double getTotalReturn() { return totalReturn; }
    public void setTotalReturn(double totalReturn) { this.totalReturn = totalReturn; }

    public double getAnnualizedReturn() { return annualizedReturn; }
    public void setAnnualizedReturn(double annualizedReturn) { this.annualizedReturn = annualizedReturn; }

    public double getVolatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }

    public double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(double sharpeRatio) { this.sharpeRatio = sharpeRatio; }

    public double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }

    public double getVar95() { return var95; }
    public void setVar95(double var95) { this.var95 = var95; }

    public double getVar99() { return var99; }
    public void setVar99(double var99) { this.var99 = var99; }

    public double getCvar95() { return cvar95; }
    public void setCvar95(double cvar95) { this.cvar95 = cvar95; }

    public double getCvar99() { return cvar99; }
    public void setCvar99(double cvar99) { this.cvar99 = cvar99; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }

    public double getProfitLossRatio() { return profitLossRatio; }
    public void setProfitLossRatio(double profitLossRatio) { this.profitLossRatio = profitLossRatio; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
