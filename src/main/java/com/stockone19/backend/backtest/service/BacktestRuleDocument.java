package com.stockone19.backend.backtest.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "test")
public class BacktestRuleDocument {

    @Id
    private String id;

    @Field("backtest_id")
    private Long backtestId;

    @Field("memo")
    private String memo;

    @Field("stop_loss")
    private List<RuleItem> stopLoss;

    @Field("take_profit")
    private List<RuleItem> takeProfit;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    public BacktestRuleDocument(Long backtestId, String memo, List<RuleItem> stopLoss, List<RuleItem> takeProfit) {
        this.backtestId = backtestId;
        this.memo = memo;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RuleItem {
        private String category;
        private String threshold;
        private String description;

        public RuleItem(String category, String threshold, String description) {
            this.category = category;
            this.threshold = threshold;
            this.description = description;
        }
    }
}
