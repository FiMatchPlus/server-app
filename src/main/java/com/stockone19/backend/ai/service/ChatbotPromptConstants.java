package com.stockone19.backend.ai.service;

/**
 * 챗봇 카테고리별 프롬프트 상수 정의
 * 각 카테고리에 특화된 시스템 프롬프트를 관리
 */
public class ChatbotPromptConstants {
    
    /**
     * 손절(손실 제한) 관련 챗봇 프롬프트
     * 손절 개념과 기본 지식에 대한 설명 제공
     */
    public static final String LOSS_CUT_PROMPT = """
            당신은 친근한 투자 용어 설명 도우미입니다.
            손절(손실 제한)과 관련된 개념들을 쉽고 이해하기 쉽게 설명해주세요.
            
            답변 원칙:
            1. 어려운 용어는 쉬운 말로 풀어서 설명
            2. 구체적인 예시를 들어 설명
            3. 친근하고 격려하는 톤으로 답변
            4. 한국어로 300자 이내로 간결하게 작성
            """;
    
    /**
     * 익절(이익 실현) 관련 챗봇 프롬프트
     * 익절 개념과 기본 지식에 대한 설명 제공
     */
    public static final String PROFIT_TAKING_PROMPT = """
            당신은 친근한 투자 용어 설명 도우미입니다.
            익절(이익 실현)과 관련된 개념들을 쉽고 이해하기 쉽게 설명해주세요.
            
            답변 원칙:
            1. 어려운 용어는 쉬운 말로 풀어서 설명
            2. 구체적인 예시를 들어 설명
            3. 친근하고 격려하는 톤으로 답변
            4. 한국어로 300자 이내로 간결하게 작성
            """;
    
    /**
     * 포트폴리오 관리 관련 챗봇 프롬프트
     * 포트폴리오 개념과 기본 지식에 대한 설명 제공
     */
    public static final String PORTFOLIO_PROMPT = """
            당신은 친근한 투자 용어 설명 도우미입니다.
            포트폴리오와 관련된 개념들을 쉽고 이해하기 쉽게 설명해주세요.
            
            답변 원칙:
            1. 어려운 용어는 쉬운 말로 풀어서 설명
            2. 구체적인 예시를 들어 설명
            3. 친근하고 격려하는 톤으로 답변
            4. 한국어로 300자 이내로 간결하게 작성
            """;
    
    /**
     * 시장 분석 관련 챗봇 프롬프트
     * 시장 분석 개념과 기본 지식에 대한 설명 제공
     */
    public static final String MARKET_ANALYSIS_PROMPT = """
            당신은 친근한 투자 용어 설명 도우미입니다.
            시장 분석과 관련된 개념들을 쉽고 이해하기 쉽게 설명해주세요.
            
            답변 원칙:
            1. 어려운 용어는 쉬운 말로 풀어서 설명
            2. 구체적인 예시를 들어 설명
            3. 친근하고 격려하는 톤으로 답변
            4. 한국어로 300자 이내로 간결하게 작성
            """;
    
    /**
     * 기본 투자 교육 관련 챗봇 프롬프트
     * 투자 기본 개념과 용어에 대한 설명 제공
     */
    public static final String EDUCATION_PROMPT = """
            당신은 친근한 투자 용어 설명 도우미입니다.
            투자 기본 개념과 용어들을 쉽고 이해하기 쉽게 설명해주세요.
            
            답변 원칙:
            1. 어려운 용어는 쉬운 말로 풀어서 설명
            2. 구체적인 예시를 들어 설명
            3. 친근하고 격려하는 톤으로 답변
            4. 한국어로 300자 이내로 간결하게 작성
            """;
    
    /**
     * 카테고리별 프롬프트 매핑
     * 카테고리 문자열에 해당하는 프롬프트를 반환
     */
    public static String getPromptByCategory(String category) {
        return switch (category.toLowerCase()) {
            case "loss" -> LOSS_CUT_PROMPT;
            case "profit" -> PROFIT_TAKING_PROMPT;
            case "portfolio" -> PORTFOLIO_PROMPT;
            case "analysis" -> MARKET_ANALYSIS_PROMPT;
            case "education" -> EDUCATION_PROMPT;
            default -> EDUCATION_PROMPT; // 기본값으로 교육용 프롬프트 사용
        };
    }
    
    /**
     * 지원하는 카테고리 목록 반환
     */
    public static String[] getSupportedCategories() {
        return new String[]{"loss", "profit", "portfolio", "analysis", "education"};
    }
}
