package com.fimatchplus.backend.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * 레포트 생성용 AI 서비스
 * GPT-4o 모델을 사용하여 정확하고 전문적인 분석 제공
 */
@Service
public class ReportAIService implements AIService {
    
    private final ChatClient chatClient;
    
    public ReportAIService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    
    @Override
    public String generateResponse(String message) {
        return generateResponse("당신은 투자 분석 전문가입니다. 정확하고 전문적인 분석을 제공해주세요.", message);
    }
    
    @Override
    public String generateResponse(String systemPrompt, String userMessage) {
        return chatClient.prompt()
                .options(OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .temperature(0.3)
                        .maxTokens(2500)
                        .build())
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }
    
}
