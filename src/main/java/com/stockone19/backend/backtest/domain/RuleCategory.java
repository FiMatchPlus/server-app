package com.stockone19.backend.backtest.domain;

/**
 * 백테스트 매매 규칙 카테고리
 * 백테스트 엔진이 인식하는 손절/익절 규칙 유형
 */
public enum RuleCategory {
    
    // ===== 손절 규칙 (Stop Loss) =====
    
    /**
     * 베타 기반 손절
     * - value: 양수 (절대값)
     * - 예시: 1.5 (포트폴리오 베타가 1.5 초과 시 손절)
     */
    BETA("BETA", RuleType.STOP_LOSS, false, false),
    
    /**
     * 최대낙폭(Maximum Drawdown) 손절
     * - value: 양수 (비율, 0~1)
     * - 예시: 0.15 (MDD가 15% 초과 시 손절)
     */
    MDD("MDD", RuleType.STOP_LOSS, true, false),
    
    /**
     * VaR(Value at Risk) 기반 손절
     * - value: 양수 (비율, 0~1)
     * - 예시: 0.05 (95% VaR이 5% 초과 시 손절)
     */
    VAR("VAR", RuleType.STOP_LOSS, true, false),
    
    /**
     * 손실 한계선
     * - value: 음수 (비율, -1~0)
     * - 예시: -0.10 (총 손실률이 -10% 미만 시 손절)
     */
    LOSS_LIMIT("LOSS_LIMIT", RuleType.STOP_LOSS, true, true),
    
    // ===== 익절 규칙 (Take Profit) =====
    
    /**
     * 단일 종목 목표 수익률
     * - value: 양수 (비율, 0~1)
     * - 예시: 0.30 (어떤 종목이든 30% 수익 시 익절)
     */
    ONEPROFIT("ONEPROFIT", RuleType.TAKE_PROFIT, true, false);
    
    private final String code;
    private final RuleType ruleType;
    private final boolean isRatio;      // 비율 기반인지 여부
    private final boolean allowNegative; // 음수 허용 여부
    
    RuleCategory(String code, RuleType ruleType, boolean isRatio, boolean allowNegative) {
        this.code = code;
        this.ruleType = ruleType;
        this.isRatio = isRatio;
        this.allowNegative = allowNegative;
    }
    
    public String getCode() {
        return code;
    }
    
    public RuleType getRuleType() {
        return ruleType;
    }
    
    public boolean isRatio() {
        return isRatio;
    }
    
    public boolean allowNegative() {
        return allowNegative;
    }
    
    /**
     * 코드로 RuleCategory 조회
     */
    public static RuleCategory fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        
        for (RuleCategory category : values()) {
            if (category.code.equalsIgnoreCase(code.trim())) {
                return category;
            }
        }
        return null;
    }
    
    /**
     * 유효한 카테고리인지 검증
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
    
    /**
     * 손절 규칙 카테고리인지 확인
     */
    public boolean isStopLoss() {
        return this.ruleType == RuleType.STOP_LOSS;
    }
    
    /**
     * 익절 규칙 카테고리인지 확인
     */
    public boolean isTakeProfit() {
        return this.ruleType == RuleType.TAKE_PROFIT;
    }
    
    /**
     * 규칙 타입
     */
    public enum RuleType {
        STOP_LOSS,   // 손절
        TAKE_PROFIT  // 익절
    }
}

