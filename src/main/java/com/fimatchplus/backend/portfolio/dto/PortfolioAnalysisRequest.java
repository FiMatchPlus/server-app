package com.fimatchplus.backend.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fimatchplus.backend.portfolio.domain.Holding;

import java.util.List;

/**
 * 포트폴리오 분석 엔진으로 보낼 요청 DTO
 */
public record PortfolioAnalysisRequest(
    @JsonProperty("portfolio_id")
    Long portfolioId,
    
    List<HoldingRequest> holdings,
    
    @JsonProperty("callback_url")
    String callbackUrl
) {
    
    public static PortfolioAnalysisRequest of(Long portfolioId, List<Holding> holdings, String callbackUrl) {
        List<HoldingRequest> holdingRequests = holdings.stream()
                .map(holding -> new HoldingRequest(holding.symbol(), holding.shares()))
                .toList();
        return new PortfolioAnalysisRequest(portfolioId, holdingRequests, callbackUrl);
    }
    
    public record HoldingRequest(
        String code,
        Integer quantity
    ) {}
}
