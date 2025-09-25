package com.stockone19.backend.backtest.repository;

import com.stockone19.backend.backtest.domain.Backtest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BacktestRepository extends JpaRepository<Backtest, Long> {

    List<Backtest> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);
    
    @Modifying
    @Query("UPDATE Backtest b SET b.status = 'FAILED' WHERE b.id = :backtestId")
    void updateBacktestStatusToFailed(@Param("backtestId") Long backtestId);
}
