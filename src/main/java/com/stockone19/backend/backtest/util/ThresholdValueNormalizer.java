package com.stockone19.backend.backtest.util;

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
    
    // 비율 기반 손절/익절 기준 카테고리
    private static final String[] RATIO_CATEGORIES = {
        "VaR", "MDD", "손실한계선", "CVaR", "최대낙폭", 
        "수익률", "목표수익률", "손실률"
    };

    /**
     * threshold 값을 정규화
     * 
     * @param category 규칙 카테고리 (예: VaR, MDD, 손실한계선)
     * @param thresholdInput 입력된 threshold 값 (예: "10%", "0.1", "10")
     * @return 정규화된 비율 값 (0~1 범위의 String)
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static String normalize(String category, String thresholdInput) {
        if (thresholdInput == null || thresholdInput.trim().isEmpty()) {
            throw new IllegalArgumentException(
                String.format("'%s'의 기준값이 비어있습니다.", category)
            );
        }

        // 비율 기반 카테고리인지 확인
        if (!isRatioCategory(category)) {
            // 비율이 아닌 카테고리는 그대로 반환 (예: 일수, 횟수 등)
            return extractNumber(thresholdInput);
        }

        // 숫자와 백분율 기호 추출
        Matcher matcher = NUMBER_PATTERN.matcher(thresholdInput.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                String.format("'%s'의 기준값이 올바른 숫자 형식이 아닙니다: %s", category, thresholdInput)
            );
        }

        String numberStr = matcher.group(1);
        boolean isPercentage = matcher.group(2) != null; // % 기호 존재 여부

        try {
            double value = Double.parseDouble(numberStr);
            
            // 백분율인 경우 비율로 변환
            if (isPercentage) {
                value = value / 100.0;
                log.debug("Converted percentage to ratio: {}% -> {}", numberStr, value);
            }

            // 유효성 검사
            validateRatioValue(category, value, thresholdInput, isPercentage);

            // 정규화된 값 반환 (소수점 6자리까지)
            return String.format("%.6f", value);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("'%s'의 기준값을 숫자로 변환할 수 없습니다: %s", category, thresholdInput)
            );
        }
    }

    /**
     * 비율 기반 카테고리인지 확인
     */
    private static boolean isRatioCategory(String category) {
        if (category == null) {
            return false;
        }
        
        for (String ratioCategory : RATIO_CATEGORIES) {
            if (category.equalsIgnoreCase(ratioCategory) || 
                category.toLowerCase().contains(ratioCategory.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 비율 값 유효성 검사
     * VaR, MDD, 손실한계선 등은 0~1 (또는 0%~100%) 범위여야 함
     */
    private static void validateRatioValue(String category, double value, 
                                          String originalInput, boolean wasPercentage) {
        // 음수 체크
        if (value < 0) {
            throw new IllegalArgumentException(
                String.format("'%s'의 기준값은 음수일 수 없습니다: %s", category, originalInput)
            );
        }

        // 비율 범위 체크
        if (value > 1.0) {
            if (wasPercentage) {
                // 백분율로 입력했는데 100을 초과하는 경우
                throw new IllegalArgumentException(
                    String.format("'%s'의 기준값은 100%%를 초과할 수 없습니다: %s", category, originalInput)
                );
            } else {
                // 비율로 입력했는데 1.0을 초과하는 경우 - 백분율로 착각한 것일 수 있음
                throw new IllegalArgumentException(
                    String.format("'%s'의 기준값은 1.0을 초과할 수 없습니다. " +
                                "백분율(%)로 입력하려면 '%s%%'와 같이 %%를 추가해주세요: %s", 
                                category, originalInput, originalInput)
                );
            }
        }

        // 0 체크 (경고)
        if (value == 0.0) {
            log.warn("'{}' 기준값이 0입니다. 이 규칙은 실질적으로 작동하지 않을 수 있습니다: {}", 
                     category, originalInput);
        }
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

