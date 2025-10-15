package com.stockone19.backend.ai.service;

import org.springframework.stereotype.Service;

/**
 * 카테고리별 특화 챗봇 서비스
 * 손절, 익절, 포트폴리오 등 특정 주제에 대한 개념 설명 제공
 */
@Service
public class CategoryChatbotService {
    
    private final ChatbotAIService chatbotAIService;
    
    public CategoryChatbotService(ChatbotAIService chatbotAIService) {
        this.chatbotAIService = chatbotAIService;
    }
    
    /**
     * 카테고리별 특화된 챗봇 응답 생성
     * 
     * @param category 챗봇 카테고리 (loss, profit, portfolio, analysis, education)
     * @param userQuestion 사용자 질문
     * @return AI 응답
     * @throws IllegalArgumentException 지원하지 않는 카테고리인 경우
     */
    public String generateCategoryResponse(String category, String userQuestion) {
        if (!isSupportedCategory(category)) {
            throw new IllegalArgumentException(
                "지원하지 않는 카테고리입니다. 지원 카테고리: " + 
                String.join(", ", ChatbotPromptConstants.getSupportedCategories())
            );
        }
        
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            throw new IllegalArgumentException("질문을 입력해주세요.");
        }
        
        String systemPrompt = ChatbotPromptConstants.getPromptByCategory(category);
        
        return chatbotAIService.generateResponse(systemPrompt, userQuestion);
    }
    
    /**
     * 카테고리 지원 여부 확인
     * 
     * @param category 확인할 카테고리
     * @return 지원 여부
     */
    public boolean isSupportedCategory(String category) {
        if (category == null) {
            return false;
        }
        
        String[] supportedCategories = ChatbotPromptConstants.getSupportedCategories();
        for (String supported : supportedCategories) {
            if (supported.equalsIgnoreCase(category)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 지원하는 카테고리 목록 반환
     * 
     * @return 카테고리 배열
     */
    public String[] getSupportedCategories() {
        return ChatbotPromptConstants.getSupportedCategories();
    }
    
    /**
     * 특정 카테고리의 설명 반환
     * 
     * @param category 카테고리
     * @return 카테고리 설명
     */
    public String getCategoryDescription(String category) {
        return switch (category.toLowerCase()) {
            case "loss" -> "손절(손실 제한) 전략과 리스크 관리에 대한 전문 조언";
            case "profit" -> "익절(이익 실현) 타이밍과 수익 극대화 전략 가이드";
            case "portfolio" -> "포트폴리오 구성과 분산투자 전략에 대한 조언";
            case "analysis" -> "시장 분석과 투자 의사결정을 위한 분석 방법론";
            case "education" -> "투자 초보자를 위한 기본 투자 지식과 교육";
            default -> "알 수 없는 카테고리입니다.";
        };
    }
}
