package com.stockone19.backend.portfolio.service;

import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.portfolio.domain.BenchmarkIndex;
import com.stockone19.backend.portfolio.domain.Holding;
import com.stockone19.backend.portfolio.domain.Portfolio;
import com.stockone19.backend.portfolio.domain.Rules;
import com.stockone19.backend.portfolio.dto.CreatePortfolioRequest;
import com.stockone19.backend.portfolio.dto.CreatePortfolioResult;
import com.stockone19.backend.portfolio.event.PortfolioCreatedEvent;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import com.stockone19.backend.portfolio.repository.RulesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PortfolioCommandService {

    private final PortfolioRepository portfolioRepository;
    private final RulesRepository rulesRepository;
    private final BenchmarkDeterminerService benchmarkDeterminerService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 새로운 포트폴리오 생성
     *
     * @param userId 사용자 ID
     * @param request 포트폴리오 생성 요청
     * @return 생성된 포트폴리오 결과
     */
    public CreatePortfolioResult createPortfolio(Long userId, CreatePortfolioRequest request) {
        log.info("Creating portfolio for userId: {}, name: {}", userId, request.name());

        // 1. Rules를 MongoDB에 저장 (선택 사항)
        String ruleId = null;
        if (request.rules() != null) {
            // Holdings 분석하여 벤치마크 결정
            List<Holding> holdingsForAnalysis = convertHoldingsFromRequest(request.holdings());
            BenchmarkIndex determinedBenchmark = benchmarkDeterminerService.determineBenchmark(holdingsForAnalysis);
            
            Rules rules = createRulesFromRequest(request.rules(), determinedBenchmark.getCode());
            Rules savedRules = rulesRepository.save(rules);
            ruleId = savedRules.getId();
            log.info("Rules saved to MongoDB with benchmark {} -> ruleId: {}", determinedBenchmark.getCode(), ruleId);
        } else {
            log.info("No rules provided. Skipping rules save.");
        }

        // 2. Portfolio 생성 (MongoDB에서 받은 ruleId 사용)
        Portfolio portfolio = Portfolio.create(
                request.name(),
                request.description(),
                ruleId, // MongoDB에서 받은 ruleId (없을 수 있음)
                false, // 새로 생성하는 포트폴리오는 메인 포트폴리오가 아님
                userId
        );
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        // 3. holdings 테이블에 보유 종목 저장
        if (request.holdings() != null && !request.holdings().isEmpty()) {
            for (CreatePortfolioRequest.HoldingRequest holdingRequest : request.holdings()) {
                Holding holding = Holding.create(
                        savedPortfolio.id(),
                        holdingRequest.symbol(),
                        holdingRequest.shares(),
                        holdingRequest.currentPrice(),
                        holdingRequest.totalValue(),
                        holdingRequest.change(),
                        holdingRequest.changePercent(),
                        holdingRequest.weight()
                );
                portfolioRepository.saveHolding(holding);
            }
        }

        // 4. 즉시 응답 데이터 생성
        CreatePortfolioResult result = new CreatePortfolioResult(
                savedPortfolio.id(),
                savedPortfolio.name(),
                savedPortfolio.description(),
                savedPortfolio.ruleId(),
                request.totalValue(),
                request.holdings().size(),
                savedPortfolio.status().name()
        );

        // 5. 포트폴리오 생성 완료 이벤트 발행 (트랜잭션 커밋 후)
        applicationEventPublisher.publishEvent(new PortfolioCreatedEvent(savedPortfolio.id()));

        return result;
    }

    /**
     * 포트폴리오 상태 업데이트
     */
    public void updatePortfolioStatus(Long portfolioId, Portfolio.PortfolioStatus status) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        Portfolio updatedPortfolio = portfolio.withStatus(status);
        portfolioRepository.save(updatedPortfolio);
        
        log.info("Updated portfolio status - portfolioId: {}, status: {}", portfolioId, status);
    }

    /**
     * 포트폴리오 분석 결과 저장
     */
    public void savePortfolioAnalysisResult(Long portfolioId, String analysisResult) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        Portfolio updatedPortfolio = portfolio.withStatusAndResult(
                Portfolio.PortfolioStatus.COMPLETED, 
                analysisResult
        );
        portfolioRepository.save(updatedPortfolio);
        
        log.info("Saved portfolio analysis result - portfolioId: {}, result length: {}", 
                portfolioId, analysisResult != null ? analysisResult.length() : 0);
    }

    /**
     * 포트폴리오 ID로 포트폴리오 조회 (읽기 전용)
     */
    @Transactional(readOnly = true)
    public Portfolio getPortfolioById(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
    }

    private Rules createRulesFromRequest(CreatePortfolioRequest.RulesRequest rulesRequest, String benchmarkCode) {
        List<Rules.RuleItem> rebalanceItems = getRuleItems(rulesRequest.rebalance());
        List<Rules.RuleItem> stopLossItems = getRuleItems(rulesRequest.stopLoss());
        List<Rules.RuleItem> takeProfitItems = getRuleItems(rulesRequest.takeProfit());

        return new Rules(
                rulesRequest.memo(),
                rebalanceItems,
                stopLossItems,
                takeProfitItems,
                benchmarkCode
        );
    }

    private static List<Rules.RuleItem> getRuleItems(List<CreatePortfolioRequest.RuleItemRequest> rulesRequest) {
        if (rulesRequest == null) {
            return List.of();
        }
        return rulesRequest.stream()
                .map(item -> new Rules.RuleItem(item.category(), item.threshold(), item.description()))
                .collect(Collectors.toList());
    }

    /**
     * CreatePortfolioRequest의 Holdings를 Holding 도메인 객체로 변환
     * (벤치마크 분석을 위해 임시 생성)
     */
    private List<Holding> convertHoldingsFromRequest(List<CreatePortfolioRequest.HoldingRequest> holdingRequests) {
        if (holdingRequests == null || holdingRequests.isEmpty()) {
            return List.of();
        }
        
        return holdingRequests.stream()
                .map(holdingRequest -> new Holding(
                        null, // temporary id
                        null, // temporary portfolioId  
                        holdingRequest.symbol(),
                        holdingRequest.shares(),
                        holdingRequest.currentPrice(),
                        holdingRequest.totalValue(),
                        holdingRequest.change(),
                        holdingRequest.changePercent(),
                        holdingRequest.weight(),
                        null, // temporary createdAt
                        null  // temporary updatedAt
                ))
                .toList();
    }
}
