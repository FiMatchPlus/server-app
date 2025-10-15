package com.fimatchplus.backend.backtest.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 백테스트 실행 로그 엔티티
 * 단방향 관계로 Backtest 참조 (OneToMany 연관관계 없음)
 */
@Getter
@Entity
@Table(name = "execution_logs")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "backtest_id", nullable = false)
    private Long backtestId;

    @Column(name = "log_date", nullable = false)
    private LocalDateTime logDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "trigger_value")
    private Double triggerValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "portfolio_value")
    private Double portfolioValue;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ExecutionLog(Long backtestId, LocalDateTime logDate, ActionType actionType, String category,
                       Double triggerValue, Double thresholdValue, String reason, Double portfolioValue) {
        this.backtestId = backtestId;
        this.logDate = logDate;
        this.actionType = actionType;
        this.category = category;
        this.triggerValue = triggerValue;
        this.thresholdValue = thresholdValue;
        this.reason = reason;
        this.portfolioValue = portfolioValue;
    }
}
