package com.stockone19.backend.backtest.repository;

import com.stockone19.backend.backtest.domain.Backtest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BacktestRepository extends JpaRepository<Backtest, Long> {

    List<Backtest> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);
}
