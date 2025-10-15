package com.fimatchplus.backend.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 포트폴리오 최적화 인사이트 리포트 생성 서비스
 * AI를 활용하여 MPT 기반 포트폴리오 최적화 결과를 분석하고 인사이트를 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioReportService {

    private final ReportAIService reportAIService;
    private final PromptTemplateService promptTemplateService;

    /**
     * 포트폴리오 분석 결과를 기반으로 최적화 인사이트 생성
     *
     * @param analysisData 포트폴리오 분석 결과 JSON 문자열
     * @return 인사이트 리포트 텍스트
     */
    public String generateOptimizationInsightFromAnalysis(String analysisData) {
        log.info("Generating portfolio optimization insight from analysis data");

        try {
            String optimizationPrompt = promptTemplateService.buildPortfolioOptimizationPrompt(analysisData);

            String report = reportAIService.generateResponse(
                    """
                            당신은 포트폴리오 최적화 전문가이자 친절한 투자 상담사입니다.
                            금융 지식이 많지 않은 일반 투자자가 포스트 모던 포트폴리오 이론(PMPT) 기반의 최적화 결과를 이해하고,
                            자신의 포트폴리오를 개선할 수 있도록 구체적이고 실행 가능한 인사이트를 제공해야 합니다.
                            
                            제공된 분석 데이터는 실제 백테스트 엔진에서 생성된 포트폴리오 최적화 결과입니다.
                            이 데이터를 바탕으로 정확하고 실용적인 투자 인사이트를 제공해주세요.
                    """,
                    optimizationPrompt
            );

            return extractJsonFromMarkdown(report);

        } catch (Exception e) {
            log.error("Failed to generate portfolio optimization insight from analysis data", e);
            throw new RuntimeException("포트폴리오 최적화 인사이트 생성에 실패했습니다.", e);
        }
    }

    /**
     * 마크다운 코드 블록에서 JSON 추출
     * LLM이 ```json ... ``` 형식으로 반환하는 경우 순수 JSON만 추출
     */
    private String extractJsonFromMarkdown(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }
        
        String trimmed = response.trim();
        
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            
            int lastBackticks = trimmed.lastIndexOf("```");
            if (lastBackticks != -1) {
                trimmed = trimmed.substring(0, lastBackticks);
            }
        }
        
        return trimmed.trim();
    }

}

