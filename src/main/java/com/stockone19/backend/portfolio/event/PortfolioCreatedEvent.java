package com.stockone19.backend.portfolio.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 포트폴리오 생성 완료 이벤트
 * 포트폴리오가 생성되고 트랜잭션이 커밋된 후 발행
 */
@Getter
public class PortfolioCreatedEvent extends ApplicationEvent {
    
    private final Long portfolioId;
    
    public PortfolioCreatedEvent(Long portfolioId) {
        super(portfolioId);
        this.portfolioId = portfolioId;
    }
}
