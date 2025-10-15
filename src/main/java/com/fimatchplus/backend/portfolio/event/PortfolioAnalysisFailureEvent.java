package com.fimatchplus.backend.portfolio.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 포트폴리오 분석 실패 이벤트
 * 포트폴리오 최적화 분석이 실패했을 때 발행
 */
@Getter
public class PortfolioAnalysisFailureEvent extends ApplicationEvent {
    
    private final Long portfolioId;
    private final String errorMessage;
    
    public PortfolioAnalysisFailureEvent(Long portfolioId, String errorMessage) {
        super(errorMessage);
        this.portfolioId = portfolioId;
        this.errorMessage = errorMessage;
    }
}
