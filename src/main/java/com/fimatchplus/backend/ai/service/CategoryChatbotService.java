package com.fimatchplus.backend.ai.service;

import com.fimatchplus.backend.backtest.domain.HoldingSnapshot;
import com.fimatchplus.backend.backtest.domain.PortfolioSnapshot;
import com.fimatchplus.backend.backtest.repository.SnapshotRepository;
import com.fimatchplus.backend.stock.repository.StockRepository;
import com.fimatchplus.backend.stock.domain.Stock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 카테고리별 특화 챗봇 서비스
 * 손절, 익절, 포트폴리오 등 특정 주제에 대한 개념 설명 제공
 */
@Slf4j
@Service
public class CategoryChatbotService {
    
    private final ChatbotAIService chatbotAIService;
    private final StockRepository stockRepository;
    private final SnapshotRepository snapshotRepository;
    
    public CategoryChatbotService(ChatbotAIService chatbotAIService, 
                                StockRepository stockRepository,
                                SnapshotRepository snapshotRepository) {
        this.chatbotAIService = chatbotAIService;
        this.stockRepository = stockRepository;
        this.snapshotRepository = snapshotRepository;
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
        return generateCategoryResponse(category, userQuestion, null);
    }
    
    /**
     * 카테고리별 특화된 챗봇 응답 생성 (백테스트 정보 포함)
     * 
     * @param category 챗봇 카테고리 (loss, profit, portfolio, analysis, education, benchmark)
     * @param userQuestion 사용자 질문
     * @param backtestId 백테스트 ID (벤치마크 카테고리일 때 포트폴리오 정보를 포함하기 위해 사용)
     * @return AI 응답
     * @throws IllegalArgumentException 지원하지 않는 카테고리인 경우
     */
    public String generateCategoryResponse(String category, String userQuestion, Long backtestId) {
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
        
        // 벤치마크 카테고리이고 백테스트 ID가 제공된 경우 포트폴리오 정보를 추가
        if ("benchmark".equalsIgnoreCase(category) && backtestId != null) {
            try {
                String portfolioInfo = getPortfolioInfoForBenchmark(backtestId);
                if (!portfolioInfo.trim().isEmpty()) {
                    systemPrompt += "\n\n=== 현재 포트폴리오 정보 ===\n" + portfolioInfo;
                }
            } catch (Exception e) {
                log.warn("포트폴리오 정보 조회 중 오류 발생 - 백테스트 ID: {}, 에러: {}", backtestId, e.getMessage());
                // 오류가 발생해도 기본 프롬프트로 계속 진행
            }
        }
        
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
            case "benchmark" -> "벤치마크 선택과 포트폴리오 성과 평가에 대한 전문 조언";
            default -> "알 수 없는 카테고리입니다.";
        };
    }
    
    /**
     * 벤치마크 분석을 위한 포트폴리오 정보 조회
     * 
     * @param backtestId 백테스트 ID
     * @return 포트폴리오 종목과 비율 정보 문자열
     */
    private String getPortfolioInfoForBenchmark(Long backtestId) {
        try {
            // 최신 포트폴리오 스냅샷 조회
            PortfolioSnapshot latestSnapshot = snapshotRepository.findLatestPortfolioSnapshotByBacktestId(backtestId);
            if (latestSnapshot == null) {
                return "포트폴리오 정보를 찾을 수 없습니다.";
            }
            
            // 최신 홀딩 스냅샷 조회
            List<HoldingSnapshot> allHoldingSnapshots = snapshotRepository.findHoldingSnapshotsByBacktestId(backtestId);
            List<HoldingSnapshot> latestHoldingSnapshots = allHoldingSnapshots.stream()
                    .filter(holding -> holding.portfolioSnapshotId().equals(latestSnapshot.id()))
                    .filter(holding -> !"PORTFOLIO_DAILY".equals(holding.stockCode())) // 포트폴리오 레벨 데이터 제외
                    .collect(Collectors.toList());
            
            if (latestHoldingSnapshots.isEmpty()) {
                return "포트폴리오 종목 정보를 찾을 수 없습니다.";
            }
            
            // 주식 코드에서 주식명으로 매핑 생성
            Map<String, String> stockCodeToNameMap = getStockCodeToNameMap(latestHoldingSnapshots);
            
            StringBuilder portfolioInfo = new StringBuilder();
            portfolioInfo.append("포트폴리오 구성 종목 및 비율:\n");
            
            // 비율 순으로 정렬하여 출력
            latestHoldingSnapshots.stream()
                    .filter(holding -> holding.weight() > 0) // 비율이 있는 종목만
                    .sorted((a, b) -> Double.compare(b.weight(), a.weight())) // 비율 내림차순 정렬
                    .forEach(holding -> {
                        String stockName = stockCodeToNameMap.getOrDefault(holding.stockCode(), holding.stockCode());
                        portfolioInfo.append(String.format("- %s: %.2f%%\n", stockName, holding.weight()));
                    });
            
            // 대형주/중소형주 비중 분석
            double largeCapRatio = calculateLargeCapRatio(latestHoldingSnapshots, stockCodeToNameMap);
            double smallCapRatio = 100.0 - largeCapRatio;
            
            portfolioInfo.append(String.format("\n대형주 비중: %.2f%%\n", largeCapRatio));
            portfolioInfo.append(String.format("중소형주 비중: %.2f%%\n", smallCapRatio));
            
            return portfolioInfo.toString();
            
        } catch (Exception e) {
            log.error("포트폴리오 정보 조회 중 오류 발생 - 백테스트 ID: {}", backtestId, e);
            return "포트폴리오 정보 조회 중 오류가 발생했습니다.";
        }
    }
    
    /**
     * 주식 코드에서 주식명으로의 매핑 생성
     */
    private Map<String, String> getStockCodeToNameMap(List<HoldingSnapshot> holdingSnapshots) {
        List<String> stockCodes = holdingSnapshots.stream()
                .map(HoldingSnapshot::stockCode)
                .collect(Collectors.toList());
        
        if (stockCodes.isEmpty()) {
            return Map.of();
        }
        
        List<Stock> stocks = stockRepository.findByTickerIn(stockCodes);
        
        return stocks.stream()
                .collect(Collectors.toMap(
                        Stock::getTicker,
                        Stock::getName,
                        (existing, replacement) -> replacement
                ));
    }
    
    /**
     * 대형주 비중 계산 (간단한 휴리스틱 사용)
     * 실제로는 더 정확한 분류 기준이 필요할 수 있음
     */
    private double calculateLargeCapRatio(List<HoldingSnapshot> holdings, Map<String, String> stockCodeToNameMap) {
        // 간단한 휴리스틱: 삼성전자, SK하이닉스 등 주요 대형주로 간주
        List<String> largeCapStocks = List.of("005930", "000660", "035420", "051910", "068270", "323410");
        
        double largeCapWeight = holdings.stream()
                .filter(holding -> largeCapStocks.contains(holding.stockCode()))
                .mapToDouble(HoldingSnapshot::weight)
                .sum();
        
        return largeCapWeight;
    }
}
