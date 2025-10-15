package com.fimatchplus.backend.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * UI 도우미용 미니 챗봇 AI 서비스
 * GPT-4o-mini 모델을 사용하여 빠르고 친근한 응답 제공
 */
@Service
public class ChatbotAIService implements AIService {
    
    private final ChatClient chatClient;
    
    public ChatbotAIService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    
    @Override
    public String generateResponse(String message) {
        return generateResponse("당신은 친근한 UI 도우미입니다. 간단명료하고 친근하게 답변해주세요.", message);
    }
    
    @Override
    public String generateResponse(String systemPrompt, String userMessage) {
        return chatClient.prompt()
                .options(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.7)
                        .maxTokens(600)
                        .build())
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }
    
}
