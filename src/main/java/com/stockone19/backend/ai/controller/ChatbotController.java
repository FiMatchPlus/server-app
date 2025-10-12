package com.stockone19.backend.ai.controller;

import com.stockone19.backend.ai.service.CategoryChatbotService;
import com.stockone19.backend.common.dto.ApiResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 카테고리별 특화 챗봇 API 컨트롤러
 * 손절, 익절, 포트폴리오 등 특정 주제에 대한 전문적인 조언 제공
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatbotController {
    
    private final CategoryChatbotService categoryChatbotService;
    
    /**
     * 카테고리별 챗봇 질문 API
     * 
     * @param category 챗봇 카테고리 (loss: 손절, profit: 익절, portfolio: 포트폴리오, analysis: 시장분석, education: 교육)
     * @param question 사용자 질문 (쿼리 파라미터)
     * @return 챗봇 응답
     */
    @GetMapping("/{category}")
    public ResponseEntity<ApiResponse<ChatbotResponse>> chat(
            @PathVariable String category,
            @RequestParam String question) {
        
        log.info("챗봇 질문 요청 - 카테고리: {}, 질문: {}", category, question);
        
        try {
            // 챗봇 응답 생성
            String response = categoryChatbotService.generateCategoryResponse(category, question);
            
            // 응답 앞 부분 로깅 (처음 100자)
            String responsePreview = response.length() > 100 
                    ? response.substring(0, 100) + "..." 
                    : response;
            log.info("챗봇 응답 생성 완료 - 카테고리: {}, 응답 미리보기: {}", category, responsePreview);
            
            // 응답 객체 생성
            ChatbotResponse chatbotResponse = ChatbotResponse.builder()
                    .category(category)
                    .categoryDescription(categoryChatbotService.getCategoryDescription(category))
                    .question(question)
                    .answer(response)
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success("챗봇 응답이 성공적으로 생성되었습니다.", chatbotResponse));
            
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 요청 - 카테고리: {}, 에러: {}", category, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
            
        } catch (Exception e) {
            log.error("챗봇 응답 생성 중 오류 발생 - 카테고리: {}, 에러: {}", category, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("챗봇 응답 생성 중 오류가 발생했습니다."));
        }
    }
    
    /**
     * 지원하는 카테고리 목록 조회 API
     * 
     * @return 지원 카테고리 목록과 설명
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryListResponse>> getCategories() {
        
        log.info("지원 카테고리 목록 조회 요청");
        
        try {
            String[] categories = categoryChatbotService.getSupportedCategories();
            CategoryInfo[] categoryInfos = new CategoryInfo[categories.length];
            
            for (int i = 0; i < categories.length; i++) {
                categoryInfos[i] = CategoryInfo.builder()
                        .category(categories[i])
                        .description(categoryChatbotService.getCategoryDescription(categories[i]))
                        .build();
            }
            
            CategoryListResponse response = CategoryListResponse.builder()
                    .categories(categoryInfos)
                    .totalCount(categories.length)
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success("지원 카테고리 목록을 성공적으로 조회했습니다.", response));
            
        } catch (Exception e) {
            log.error("카테고리 목록 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("카테고리 목록 조회 중 오류가 발생했습니다."));
        }
    }
    
    /**
     * 챗봇 응답 DTO
     */
    @Builder
    @Getter
    public static class ChatbotResponse {
        private final String category;
        private final String categoryDescription;
        private final String question;
        private final String answer;
    }
    
    /**
     * 카테고리 정보 DTO
     */
    @Builder
    @Getter
    public static class CategoryInfo {
        private final String category;
        private final String description;
    }
    
    /**
     * 카테고리 목록 응답 DTO
     */
    @Builder
    @Getter
    public static class CategoryListResponse {
        private final CategoryInfo[] categories;
        private final int totalCount;
    }
}
