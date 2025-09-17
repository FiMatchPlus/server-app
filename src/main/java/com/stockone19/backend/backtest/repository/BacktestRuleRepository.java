package com.stockone19.backend.backtest.repository;

import com.stockone19.backend.backtest.service.BacktestRuleDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BacktestRuleRepository extends MongoRepository<BacktestRuleDocument, String> {
}
