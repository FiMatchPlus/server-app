package com.fimatchplus.backend.portfolio.event;

import com.fimatchplus.backend.portfolio.dto.PortfolioAnalysisResponse;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 포트폴리오 분석 성공 이벤트
 * 포트폴리오 최적화 분석이 성공적으로 완료되었을 때 발행
 */
@Getter
public class PortfolioAnalysisSuccessEvent extends ApplicationEvent {
    
    private final Long portfolioId;
    private final PortfolioAnalysisResponse analysisResponse;
    
    public PortfolioAnalysisSuccessEvent(Long portfolioId, PortfolioAnalysisResponse analysisResponse) {
        super(analysisResponse);
        this.portfolioId = portfolioId;
        this.analysisResponse = analysisResponse;
    }
}
