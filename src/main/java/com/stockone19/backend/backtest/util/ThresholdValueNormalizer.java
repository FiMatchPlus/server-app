package com.stockone19.backend.backtest.util;

import com.stockone19.backend.backtest.domain.RuleCategory;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 손절/익절 기준 값 정규화 유틸리티
 * 백분율(%)을 비율(0~1)로 변환하고 유효성 검사를 수행
 */
@Slf4j
public class ThresholdValueNormalizer {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("([+-]?\\d*\\.?\\d+)\\s*(%)?");

    /**
     * threshold 값을 정규화
     * 
     * @param categoryCode 규칙 카테고리 코드 (BETA, MDD, VAR, LOSS_LIMIT, ONEPROFIT)
     * @param thresholdInput 입력된 threshold 값 (예: "10%", "0.1", "1.5")
     * @return 정규화된 값 (비율 카테고리는 0~1 범위, BETA는 절대값)
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static String normalize(String categoryCode, String thresholdInput) {
        if (thresholdInput == null || thresholdInput.trim().isEmpty()) {
            throw new IllegalArgumentException(
                String.format("'%s'의 기준값이 비어있습니다.", categoryCode)
            );
        }

        RuleCategory category = RuleCategory.fromCode(categoryCode);
        if (category == null) {
            log.warn("Unknown category '{}', extracting number only", categoryCode);
            return extractNumber(thresholdInput);
        }

        // 숫자와 백분율 기호 추출
        Matcher matcher = NUMBER_PATTERN.matcher(thresholdInput.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                String.format("'%s'의 기준값이 올바른 숫자 형식이 아닙니다: %s", categoryCode, thresholdInput)
            );
        }

        String numberStr = matcher.group(1);
        boolean isPercentage = matcher.group(2) != null; // % 기호 존재 여부

        try {
            double value = Double.parseDouble(numberStr);
            
            // 비율 기반 카테고리인 경우 백분율을 비율로 변환
            if (category.isRatio() && isPercentage) {
                value = value / 100.0;
                log.debug("Converted percentage to ratio: {}% -> {}", numberStr, value);
            }

            // 유효성 검사 및 카테고리별 자동 변환 (LOSS_LIMIT은 자동으로 음수 변환)
            value = validateValue(category, value, thresholdInput, isPercentage);

            // 정규화된 값 반환 (소수점 6자리까지)
            return String.format("%.6f", value);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("'%s'의 기준값을 숫자로 변환할 수 없습니다: %s", categoryCode, thresholdInput)
            );
        }
    }

    /**
     * 카테고리별 값 유효성 검사 및 변환
     * @return 검증 및 변환된 최종 값
     */
    private static double validateValue(RuleCategory category, double value, 
                                        String originalInput, boolean wasPercentage) {
        String categoryCode = category.getCode();
        
        // BETA는 양수 절대값
        if (category == RuleCategory.BETA) {
            if (value <= 0) {
                throw new IllegalArgumentException(
                    String.format("'%s'의 기준값은 0보다 커야 합니다: %s", categoryCode, originalInput)
                );
            }
            return value;
        }
        
        // LOSS_LIMIT은 음수 비율만 허용 (양수 입력 시 자동으로 음수 변환)
        if (category == RuleCategory.LOSS_LIMIT) {
            // 양수로 입력된 경우 자동으로 음수로 변환
            if (value > 0) {
                log.info("'{}' 카테고리는 음수 값이 필요합니다. 입력값 '{}' -> '{}'로 자동 변환", 
                        categoryCode, value, -value);
                value = -value;
            }
            
            if (value < -1.0) {
                throw new IllegalArgumentException(
                    String.format("'%s'의 기준값은 -100%%보다 작을 수 없습니다: %s", categoryCode, originalInput)
                );
            }
            
            if (value == 0.0) {
                log.warn("'{}' 기준값이 0입니다. 이 규칙은 실질적으로 작동하지 않을 수 있습니다: {}", categoryCode, originalInput);
            }
            
            return value;
        }
        
        // 그 외 비율 기반 카테고리 (MDD, VAR, ONEPROFIT): 양수 비율
        if (category.isRatio()) {
            if (value < 0) {
                throw new IllegalArgumentException(
                    String.format("'%s'의 기준값은 음수일 수 없습니다: %s", categoryCode, originalInput)
                );
            }

            if (value > 1.0) {
                if (wasPercentage) {
                    throw new IllegalArgumentException(
                        String.format("'%s'의 기준값은 100%%를 초과할 수 없습니다: %s", categoryCode, originalInput)
                    );
                } else {
                    throw new IllegalArgumentException(
                        String.format("'%s'의 기준값은 1.0을 초과할 수 없습니다. 백분율(%)로 입력하려면 '%s%%'와 같이 %%를 추가해주세요: %s",
                                    categoryCode, originalInput, originalInput)
                    );
                }
            }

            if (value == 0.0) {
                log.warn("'{}' 기준값이 0입니다. 이 규칙은 실질적으로 작동하지 않을 수 있습니다: {}", categoryCode, originalInput);
            }
        }
        
        return value;
    }

    /**
     * 문자열에서 숫자만 추출 (비율 카테고리가 아닌 경우)
     */
    private static String extractNumber(String input) {
        Matcher matcher = NUMBER_PATTERN.matcher(input.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                String.format("올바른 숫자 형식이 아닙니다: %s", input)
            );
        }
        
        return matcher.group(1);
    }

    /**
     * 정규화된 값이 유효한지 검증 (Double로 파싱 가능한지 확인)
     */
    public static boolean isValid(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.trim().isEmpty()) {
            return false;
        }
        
        try {
            Double.parseDouble(normalizedValue);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

