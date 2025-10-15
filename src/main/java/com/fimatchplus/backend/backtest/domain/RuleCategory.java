package com.fimatchplus.backend.backtest.domain;

import lombok.Getter;

/**
 * 백테스트 매매 규칙 카테고리
 * 백테스트 엔진이 인식하는 손절/익절 규칙 유형
 */
public enum RuleCategory {
    
    
    /**
     * 베타 기반 손절
     * - value: 양수 (절대값)
     * - 예시: 1.5 (포트폴리오 베타가 1.5 초과 시 손절)
     */
    BETA("BETA", "베타 일정값 초과", RuleType.STOP_LOSS, false, false),
    
    /**
     * 최대낙폭(Maximum Drawdown) 손절
     * - value: 양수 (비율, 0~1)
     * - 예시: 0.15 (MDD가 15% 초과 시 손절)
     */
    MDD("MDD", "MDD 초과", RuleType.STOP_LOSS, true, false),
    
    /**
     * VaR(Value at Risk) 기반 손절
     * - value: 양수 (비율, 0~1)
     * - 예시: 0.05 (95% VaR이 5% 초과 시 손절)
     */
    VAR("VAR", "VaR 초과", RuleType.STOP_LOSS, true, false),
    
    /**
     * 손실 한계선
     * - value: 음수 (비율, -1~0)
     * - 예시: -0.10 (총 손실률이 -10% 미만 시 손절)
     */
    LOSS_LIMIT("LOSS_LIMIT", "손실 한계선", RuleType.STOP_LOSS, true, true),
    
    
    /**
     * 단일 종목 목표 수익률
     * - value: 양수 (비율, 0~1)
     * - 예시: 0.30 (어떤 종목이든 30% 수익 시 익절)
     */
    ONEPROFIT("ONEPROFIT", "단일 종목 목표 수익률 달성", RuleType.TAKE_PROFIT, true, false);
    
    @Getter
    private final String code;
    @Getter
    private final String name;
    @Getter
    private final RuleType ruleType;
    private final boolean isRatio;
    private final boolean allowNegative;
    
    RuleCategory(String code, String name, RuleType ruleType, boolean isRatio, boolean allowNegative) {
        this.code = code;
        this.name = name;
        this.ruleType = ruleType;
        this.isRatio = isRatio;
        this.allowNegative = allowNegative;
    }

    public boolean isRatio() {
        return isRatio;
    }
    
    public boolean allowNegative() {
        return allowNegative;
    }
    
    /**
     * 코드 또는 이름으로 RuleCategory 조회
     */
    public static RuleCategory fromCode(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        
        String trimmedInput = input.trim();
        
        for (RuleCategory category : values()) {
            if (category.code.equalsIgnoreCase(trimmedInput)) {
                return category;
            }
            if (category.name.equals(trimmedInput)) {
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
        STOP_LOSS,
        TAKE_PROFIT
    }
}

