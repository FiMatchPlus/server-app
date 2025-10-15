package com.fimatchplus.backend.portfolio.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "rules")
public class Rules {

    @Id
    private String id;

    @Field("memo")
    private String memo;

    @Field("rebalance")
    private List<RuleItem> rebalance;

    @Field("stop_loss")
    private List<RuleItem> stopLoss;

    @Field("take_profit")
    private List<RuleItem> takeProfit;

    @Field("basic_benchmark")
    private String basicBenchmark;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    public Rules() {}

    public Rules(String memo, List<RuleItem> rebalance, List<RuleItem> stopLoss, List<RuleItem> takeProfit) {
        this.memo = memo;
        this.rebalance = rebalance;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.basicBenchmark = null;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Rules(String memo, List<RuleItem> rebalance, List<RuleItem> stopLoss, List<RuleItem> takeProfit, String basicBenchmark) {
        this.memo = memo;
        this.rebalance = rebalance;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.basicBenchmark = basicBenchmark;
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

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getThreshold() { return threshold; }
        public void setThreshold(String threshold) { this.threshold = threshold; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public List<RuleItem> getRebalance() { return rebalance; }
    public void setRebalance(List<RuleItem> rebalance) { this.rebalance = rebalance; }

    public List<RuleItem> getStopLoss() { return stopLoss; }
    public void setStopLoss(List<RuleItem> stopLoss) { this.stopLoss = stopLoss; }

    public List<RuleItem> getTakeProfit() { return takeProfit; }
    public void setTakeProfit(List<RuleItem> takeProfit) { this.takeProfit = takeProfit; }

    public String getBasicBenchmark() { return basicBenchmark; }
    public void setBasicBenchmark(String basicBenchmark) { this.basicBenchmark = basicBenchmark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
