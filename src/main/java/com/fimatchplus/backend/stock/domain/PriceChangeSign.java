package com.fimatchplus.backend.stock.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * KIS API의 prdy_vrss_sign (전일대비부호)
 */
public enum PriceChangeSign {
    UPPER_LIMIT("1", "상한"),
    RISE("2", "상승"),
    FLAT("3", "보합"),
    LOWER_LIMIT("4", "하한"),
    FALL("5", "하락");

    private final String code;
    @Getter
    private final String description;

    PriceChangeSign(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * KIS API에서 받은 부호 코드를 enum으로 변환
     */
    public static PriceChangeSign fromCode(String code) {
        if (code == null) {
            return FLAT;
        }
        
        for (PriceChangeSign sign : values()) {
            if (sign.code.equals(code)) {
                return sign;
            }
        }
        
        return FLAT;
    }

}
