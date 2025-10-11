package com.stockone19.backend.backtest.repository;

import com.stockone19.backend.backtest.domain.Backtest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BacktestRepository extends JpaRepository<Backtest, Long> {

    @Query("SELECT b FROM Backtest b WHERE b.portfolioId = :portfolioId AND b.deletedAt IS NULL ORDER BY b.createdAt DESC")
    List<Backtest> findByPortfolioIdOrderByCreatedAtDesc(@Param("portfolioId") Long portfolioId);
    
    @Query("SELECT b FROM Backtest b WHERE b.id = :id AND b.deletedAt IS NULL")
    Optional<Backtest> findById(@Param("id") Long id);
    
    @Modifying
    @Query("UPDATE Backtest b SET b.status = 'FAILED' WHERE b.id = :backtestId AND b.deletedAt IS NULL")
    void updateBacktestStatusToFailed(@Param("backtestId") Long backtestId);
    
    @Modifying
    @Query("UPDATE Backtest b SET b.deletedAt = CURRENT_TIMESTAMP WHERE b.id = :backtestId AND b.deletedAt IS NULL")
    void softDelete(@Param("backtestId") Long backtestId);
}
