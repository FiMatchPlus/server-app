package com.fimatchplus.backend.backtest.domain;

/**
 * 벤치마크 지수 Enum
 */
public enum BenchmarkIndex {
    KOSPI("KOSPI", "KOSPI 지수"),
    KOSDAQ("KOSDAQ", "KOSDAQ 지수");

    private final String code;
    private final String name;

    BenchmarkIndex(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /**
     * 코드로 벤치마크 지수 조회
     */
    public static BenchmarkIndex fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        
        for (BenchmarkIndex index : values()) {
            if (index.code.equalsIgnoreCase(code.trim())) {
                return index;
            }
        }
        return null;
    }

    /**
     * 코드로 벤치마크 이름 조회
     */
    public static String getNameByCode(String code) {
        BenchmarkIndex index = fromCode(code);
        return index != null ? index.getName() : null;
    }
}
