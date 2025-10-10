package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.stockone19.backend.backtest.service.BacktestRuleDocument;

import java.time.LocalDateTime;

/**
 * 백테스트 메타데이터 (설정 정보)
 * 백테스트 생성 시 설정한 정보들을 반환
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BacktestMetaData(
        Long backtestId,
        Long portfolioId,
        String title,
        String description,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startAt,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endAt,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        
        String benchmarkCode,
        BacktestStatus status,
        BacktestRuleDocument rules
) {
    
    /**
     * 백테스트 메타데이터 생성
     */
    public static BacktestMetaData of(
            Long backtestId,
            Long portfolioId,
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime createdAt,
            String benchmarkCode,
            BacktestStatus status,
            BacktestRuleDocument rules
    ) {
        return new BacktestMetaData(
                backtestId,
                portfolioId,
                title,
                description,
                startAt,
                endAt,
                createdAt,
                benchmarkCode,
                status,
                rules
        );
    }
}

