package com.stockone19.backend.backtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백테스트 생성 요청 DTO
 */
public record CreateBacktestRequest(
        @NotBlank(message = "백테스트 제목은 필수입니다")
        String title,

        String description,

        @NotNull(message = "시작일은 필수입니다")
        LocalDateTime startAt,

        @NotNull(message = "종료일은 필수입니다")
        LocalDateTime endAt,

        RulesRequest rules,
        
        @NotNull(message = "벤치마크 지수는 필수입니다")
        String benchmarkCode  // "KOSPI" 또는 "KOSDAQ"
) {

    /**
     * 매매 규칙 (손절/익절)
     */
    public record RulesRequest(
            String memo,
            List<RuleItemRequest> stopLoss,    // 손절 규칙
            List<RuleItemRequest> takeProfit   // 익절 규칙
    ) {}

    /**
     * 개별 규칙 항목
     * 
     * <p><b>손절 규칙 (stopLoss) - 사용 가능한 category:</b>
     * <ul>
     *   <li><b>BETA</b>: 베타 기반 손절 (양수 절대값, 예: "1.5")</li>
     *   <li><b>MDD</b>: 최대낙폭 손절 (양수 비율, 예: "15%" 또는 "0.15")</li>
     *   <li><b>VAR</b>: VaR 기반 손절 (양수 비율, 예: "5%" 또는 "0.05")</li>
     *   <li><b>LOSS_LIMIT</b>: 손실 한계선 (비율, 예: "10%" → 자동으로 -0.1로 변환)</li>
     * </ul>
     * 
     * <p><b>익절 규칙 (takeProfit) - 사용 가능한 category:</b>
     * <ul>
     *   <li><b>ONEPROFIT</b>: 단일 종목 목표 수익률 (양수 비율, 예: "30%" 또는 "0.3")</li>
     * </ul>
     * 
     * <p><b>참고:</b> LOSS_LIMIT은 양수로 입력해도 자동으로 음수로 변환됩니다.
     * 
     * @param category 규칙 카테고리 (위 목록 참고)
     * @param threshold 기준값 ("10%", "0.1", "1.5" 등)
     * @param description 규칙 설명 (선택사항)
     */
    public record RuleItemRequest(
            String category,
            String threshold,
            String description
    ) {}
}
