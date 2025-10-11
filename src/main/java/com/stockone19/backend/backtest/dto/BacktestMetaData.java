package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stockone19.backend.backtest.service.BacktestRuleDocument;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
        String startAt,        // "yyyy-MM-dd'T'HH:mm:ss" 형식
        String endAt,          // "yyyy-MM-dd'T'HH:mm:ss" 형식
        String period,         // "yyyy-MM-dd ~ yyyy-MM-dd" 형식
        String createdAt,      // "yyyy-MM-dd'T'HH:mm:ss" 형식
        String benchmarkCode,
        BacktestStatus status,
        BacktestRuleDocument rules
) {
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
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
        // 날짜를 문자열로 변환
        String startAtStr = startAt != null ? startAt.format(DATE_TIME_FORMATTER) : null;
        String endAtStr = endAt != null ? endAt.format(DATE_TIME_FORMATTER) : null;
        String createdAtStr = createdAt != null ? createdAt.format(DATE_TIME_FORMATTER) : null;
        
        // 기간 문자열 생성 (yyyy-MM-dd ~ yyyy-MM-dd 형식)
        String period = (startAt != null && endAt != null) 
            ? startAt.format(DATE_FORMATTER) + " ~ " + endAt.format(DATE_FORMATTER)
            : null;
        
        return new BacktestMetaData(
                backtestId,
                portfolioId,
                title,
                description,
                startAtStr,
                endAtStr,
                period,
                createdAtStr,
                benchmarkCode,
                status,
                rules
        );
    }
}

