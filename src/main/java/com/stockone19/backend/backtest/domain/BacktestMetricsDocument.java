package com.stockone19.backend.backtest.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * MongoDB에 저장되는 백테스트 성과 지표 문서
 */
@Getter
@Setter
@NoArgsConstructor
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
}
