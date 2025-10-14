package com.stockone19.backend.portfolio.dto;

import com.stockone19.backend.portfolio.domain.Portfolio;

/**
 * 포트폴리오 분석 상태 응답 DTO
 * 프론트엔드에서 폴링을 통해 분석 진행 상태를 추적하기 위한 응답
 */
public record PortfolioStatusResponse(
        Long portfolioId,
        String status  // "PENDING" | "RUNNING" | "COMPLETED" | "FAILED"
) {
    public static PortfolioStatusResponse from(Portfolio portfolio) {
        return new PortfolioStatusResponse(
                portfolio.id(),
                portfolio.status().name()
        );
    }
}

