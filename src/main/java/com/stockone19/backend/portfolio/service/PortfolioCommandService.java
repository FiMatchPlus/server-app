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
     * 포트폴리오 레포트 결과 저장
     */
    public void savePortfolioReportResult(Long portfolioId, String reportResult) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        Portfolio updatedPortfolio = portfolio.withReportResult(reportResult);
        portfolioRepository.save(updatedPortfolio);
        
        log.info("Saved portfolio report result - portfolioId: {}, report length: {}", 
                portfolioId, reportResult != null ? reportResult.length() : 0);
    }

    /**
     * 포트폴리오 ID로 포트폴리오 조회 (읽기 전용)
     */
    @Transactional(readOnly = true)
    public Portfolio getPortfolioById(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
    }

    /**
     * 포트폴리오 수정
     *
     * @param portfolioId 수정할 포트폴리오 ID
     * @param userId 사용자 ID
     * @param request 수정 요청
     */
    public void updatePortfolio(Long portfolioId, Long userId, com.stockone19.backend.portfolio.dto.UpdatePortfolioRequest request) {
        log.info("Updating portfolio - portfolioId: {}, userId: {}, name: {}", portfolioId, userId, request.name());

        // 1. 기존 포트폴리오 조회 및 권한 확인
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        if (!portfolio.userId().equals(userId)) {
            throw new ResourceNotFoundException("포트폴리오 수정 권한이 없습니다: " + portfolioId);
        }

        // 2. 기본 정보 업데이트 및 분석 상태 초기화
        Portfolio updatedPortfolio = portfolio
                .withNameAndDescription(request.name(), request.description())
                .withStatusAndReports(Portfolio.PortfolioStatus.PENDING, null, null);
        portfolioRepository.save(updatedPortfolio);

        // 3. 기존 holdings 삭제
        portfolioRepository.deleteHoldingsByPortfolioId(portfolioId);

        // 4. 새로운 holdings 저장
        if (request.holdings() != null && !request.holdings().isEmpty()) {
            for (com.stockone19.backend.portfolio.dto.UpdatePortfolioRequest.HoldingRequest holdingRequest : request.holdings()) {
                Holding holding = Holding.create(
                        portfolioId,
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

        // 5. Rules 업데이트 (선택 사항)
        if (request.rules() != null && portfolio.ruleId() != null) {
            // Holdings 분석하여 벤치마크 결정
            List<Holding> holdingsForAnalysis = convertUpdateHoldingsFromRequest(request.holdings());
            BenchmarkIndex determinedBenchmark = benchmarkDeterminerService.determineBenchmark(holdingsForAnalysis);
            
            Rules rules = createRulesFromRequest(request.rules(), determinedBenchmark.getCode());
            rules.setId(portfolio.ruleId()); // 기존 ruleId 사용
            rulesRepository.save(rules);
            log.info("Rules updated in MongoDB - ruleId: {}", portfolio.ruleId());
        }

        log.info("Portfolio updated successfully - portfolioId: {}", portfolioId);
        
        // 6. 포트폴리오 수정 완료 이벤트 발행 (트랜잭션 커밋 후 재분석 트리거)
        applicationEventPublisher.publishEvent(new PortfolioCreatedEvent(portfolioId));
    }

    /**
     * 포트폴리오 삭제 (Soft Delete)
     *
     * @param portfolioId 삭제할 포트폴리오 ID
     * @param userId 사용자 ID
     */
    public void deletePortfolio(Long portfolioId, Long userId) {
        log.info("Deleting portfolio - portfolioId: {}, userId: {}", portfolioId, userId);

        // 1. 기존 포트폴리오 조회 및 권한 확인
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        if (!portfolio.userId().equals(userId)) {
            throw new ResourceNotFoundException("포트폴리오 삭제 권한이 없습니다: " + portfolioId);
        }

        // 2. Soft delete 수행
        portfolioRepository.softDelete(portfolioId);

        log.info("Portfolio soft deleted successfully - portfolioId: {}", portfolioId);
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

    private Rules createRulesFromRequest(com.stockone19.backend.portfolio.dto.UpdatePortfolioRequest.RulesRequest rulesRequest, String benchmarkCode) {
        List<Rules.RuleItem> rebalanceItems = getUpdateRuleItems(rulesRequest.rebalance());
        List<Rules.RuleItem> stopLossItems = getUpdateRuleItems(rulesRequest.stopLoss());
        List<Rules.RuleItem> takeProfitItems = getUpdateRuleItems(rulesRequest.takeProfit());

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

    private static List<Rules.RuleItem> getUpdateRuleItems(List<com.stockone19.backend.portfolio.dto.UpdatePortfolioRequest.RuleItemRequest> rulesRequest) {
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

    /**
     * UpdatePortfolioRequest의 Holdings를 Holding 도메인 객체로 변환
     * (벤치마크 분석을 위해 임시 생성)
     */
    private List<Holding> convertUpdateHoldingsFromRequest(List<com.stockone19.backend.portfolio.dto.UpdatePortfolioRequest.HoldingRequest> holdingRequests) {
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
