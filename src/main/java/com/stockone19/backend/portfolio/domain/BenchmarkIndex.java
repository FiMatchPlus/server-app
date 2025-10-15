package com.stockone19.backend.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

/**
 * 벤치마크 지수 Enum
 * 포트폴리오 분석 및 백테스트 비교 지수
 */
@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum BenchmarkIndex {
    KOSPI("KOSPI", "코스피 종합주가지수", "KOSPI 종합주가지수  ※ 코스피, 크로스 마켓 대형주 중심"),
    KOSDAQ("KOSDAQ", "코스닥 종합주가지수", "KOSDAQ 종합주가지수   ※ 기술주의, 성장주 중심");
    
    private final String code;
    private final String name;
    private final String description;
    
    BenchmarkIndex(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
    
    /**
     * 벤치마크 지수 코드로 찾기
     * @param code 비교할 벤치마크 코드
     * @return 해당 벤치마크 지수 또는 null
     */
    public static BenchmarkIndex fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        
        for (BenchmarkIndex benchmark : BenchmarkIndex.values()) {
            if (benchmark.code.equals(code.trim().toUpperCase())) {
                return benchmark;
            }
        }
        
        return null;
    }
    
    /**
     * valid한 벤치마크 하이 선택
     * @param expectedMarket 예상 시장 ("KOSPI", "KOSDAQ")
     * @return 기대되는 벤치마크, 없으면 기본 KOSPI
     */
    public static BenchmarkIndex selectBestBenachmart(String expectedMarket) {
        if (expectedMarket == null) {
            return KOSPI;
        }
        
        String normalized = expectedMarket.trim().toUpperCase();
        return switch (normalized) {
            case "KOSPI" -> KOSPI;
            case "KOSDAQ" -> KOSDAQ;
            default -> KOSPI;
        };
    }
}
