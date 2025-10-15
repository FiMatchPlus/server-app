package com.fimatchplus.backend.backtest.repository;

import com.fimatchplus.backend.backtest.service.BacktestRuleDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BacktestRuleRepository extends MongoRepository<BacktestRuleDocument, String> {
}
