package com.stockone19.backend.backtest.service;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

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

    public BacktestRuleDocument() {}

    public BacktestRuleDocument(Long backtestId, String memo, List<RuleItem> stopLoss, List<RuleItem> takeProfit) {
        this.backtestId = backtestId;
        this.memo = memo;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static class RuleItem {
        private String category;
        private String threshold;
        private String description;

        public RuleItem() {}

        public RuleItem(String category, String threshold, String description) {
            this.category = category;
            this.threshold = threshold;
            this.description = description;
        }

        // Getters and Setters
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getThreshold() { return threshold; }
        public void setThreshold(String threshold) { this.threshold = threshold; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getBacktestId() { return backtestId; }
    public void setBacktestId(Long backtestId) { this.backtestId = backtestId; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public List<RuleItem> getStopLoss() { return stopLoss; }
    public void setStopLoss(List<RuleItem> stopLoss) { this.stopLoss = stopLoss; }

    public List<RuleItem> getTakeProfit() { return takeProfit; }
    public void setTakeProfit(List<RuleItem> takeProfit) { this.takeProfit = takeProfit; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
