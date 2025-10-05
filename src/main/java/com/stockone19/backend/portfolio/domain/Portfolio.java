package com.stockone19.backend.portfolio.domain;

import java.time.LocalDateTime;

public record Portfolio(
        Long id,
        String name,
        String description,
        String ruleId,
        boolean isMain,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long userId,
        PortfolioStatus status,
        String analysisResult
) {

    public enum PortfolioStatus {
        PENDING,    // 분석 대기 중
        RUNNING,    // 분석 실행 중
        COMPLETED,  // 분석 완료
        FAILED      // 분석 실패
    }

    public static Portfolio of(
            Long id,
            String name,
            String description,
            String ruleId,
            boolean isMain,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long userId,
            PortfolioStatus status,
            String analysisResult
    ) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult
        );
    }

    public static Portfolio create(
            String name,
            String description,
            String ruleId,
            boolean isMain,
            Long userId
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new Portfolio(
                null, name, description, ruleId, isMain, now, now, userId, 
                PortfolioStatus.PENDING, null
        );
    }

    public Portfolio withId(Long id) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult
        );
    }

    public Portfolio withUpdatedAt(LocalDateTime updatedAt) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult
        );
    }

    public Portfolio withStatus(PortfolioStatus status) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult
        );
    }

    public Portfolio withAnalysisResult(String analysisResult) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult
        );
    }

    public Portfolio withStatusAndResult(PortfolioStatus status, String analysisResult) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult
        );
    }
}