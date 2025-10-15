package com.fimatchplus.backend.ai.service;

/**
 * AI 서비스 기본 인터페이스
 * 모든 AI 서비스가 구현해야 하는 공통 메서드를 정의
 */
public interface AIService {
    
    /**
     * AI 모델에 메시지를 전송하고 응답을 받는 기본 메서드
     * 
     * @param message 사용자 메시지
     * @return AI 응답
     */
    String generateResponse(String message);
    
    /**
     * 시스템 프롬프트와 함께 메시지를 전송
     * 
     * @param systemPrompt 시스템 프롬프트 (AI의 역할과 행동을 정의)
     * @param userMessage 사용자 메시지
     * @return AI 응답
     */
    String generateResponse(String systemPrompt, String userMessage);
    
}
