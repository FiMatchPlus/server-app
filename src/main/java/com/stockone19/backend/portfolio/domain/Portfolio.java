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
        String analysisResult,
        String reportResult,
        LocalDateTime deletedAt
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
            String analysisResult,
            String reportResult,
            LocalDateTime deletedAt
    ) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
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
                PortfolioStatus.PENDING, null, null, null
        );
    }

    public Portfolio withId(Long id) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public Portfolio withUpdatedAt(LocalDateTime updatedAt) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public Portfolio withStatus(PortfolioStatus status) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public Portfolio withAnalysisResult(String analysisResult) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public Portfolio withStatusAndResult(PortfolioStatus status, String analysisResult) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public Portfolio withReportResult(String reportResult) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public Portfolio withStatusAndReports(PortfolioStatus status, String analysisResult, String reportResult) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public Portfolio withDeletedAt(LocalDateTime deletedAt) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, updatedAt, userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public Portfolio withNameAndDescription(String name, String description) {
        return new Portfolio(
                id, name, description, ruleId, isMain, createdAt, LocalDateTime.now(), userId, status, analysisResult, reportResult, deletedAt
        );
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}