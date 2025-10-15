package com.fimatchplus.backend.portfolio.service;

import com.fimatchplus.backend.common.exception.ResourceNotFoundException;
import com.fimatchplus.backend.portfolio.domain.BenchmarkIndex;
import com.fimatchplus.backend.portfolio.domain.Holding;
import com.fimatchplus.backend.portfolio.domain.Portfolio;
import com.fimatchplus.backend.portfolio.domain.Rules;
import com.fimatchplus.backend.portfolio.dto.CreatePortfolioRequest;
import com.fimatchplus.backend.portfolio.dto.CreatePortfolioResult;
import com.fimatchplus.backend.portfolio.event.PortfolioCreatedEvent;
import com.fimatchplus.backend.portfolio.repository.PortfolioRepository;
import com.fimatchplus.backend.portfolio.repository.RulesRepository;
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

        String ruleId = null;
        if (request.rules() != null) {
            List<Holding> holdingsForAnalysis = convertHoldingsFromRequest(request.holdings());
            BenchmarkIndex determinedBenchmark = benchmarkDeterminerService.determineBenchmark(holdingsForAnalysis);
            
            Rules rules = createRulesFromRequest(request.rules(), determinedBenchmark.getCode());
            Rules savedRules = rulesRepository.save(rules);
            ruleId = savedRules.getId();
            log.info("Rules saved to MongoDB with benchmark {} -> ruleId: {}", determinedBenchmark.getCode(), ruleId);
        } else {
            log.info("No rules provided. Skipping rules save.");
        }

        Portfolio portfolio = Portfolio.create(
                request.name(),
                request.description(),
                ruleId,
                false,
                userId
        );
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

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

        CreatePortfolioResult result = new CreatePortfolioResult(
                savedPortfolio.id(),
                savedPortfolio.name(),
                savedPortfolio.description(),
                savedPortfolio.ruleId(),
                request.totalValue(),
                request.holdings().size(),
                savedPortfolio.status().name()
        );

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
    public void updatePortfolio(Long portfolioId, Long userId, com.fimatchplus.backend.portfolio.dto.UpdatePortfolioRequest request) {
        log.info("Updating portfolio - portfolioId: {}, userId: {}, name: {}", portfolioId, userId, request.name());

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        if (!portfolio.userId().equals(userId)) {
            throw new ResourceNotFoundException("포트폴리오 수정 권한이 없습니다: " + portfolioId);
        }

        Portfolio updatedPortfolio = portfolio
                .withNameAndDescription(request.name(), request.description())
                .withStatusAndReports(Portfolio.PortfolioStatus.PENDING, null, null);
        portfolioRepository.save(updatedPortfolio);

        portfolioRepository.deleteHoldingsByPortfolioId(portfolioId);

        if (request.holdings() != null && !request.holdings().isEmpty()) {
            for (com.fimatchplus.backend.portfolio.dto.UpdatePortfolioRequest.HoldingRequest holdingRequest : request.holdings()) {
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

        if (request.rules() != null && portfolio.ruleId() != null) {
            List<Holding> holdingsForAnalysis = convertUpdateHoldingsFromRequest(request.holdings());
            BenchmarkIndex determinedBenchmark = benchmarkDeterminerService.determineBenchmark(holdingsForAnalysis);
            
            Rules rules = createRulesFromRequest(request.rules(), determinedBenchmark.getCode());
            rules.setId(portfolio.ruleId());
            rulesRepository.save(rules);
            log.info("Rules updated in MongoDB - ruleId: {}", portfolio.ruleId());
        }

        log.info("Portfolio updated successfully - portfolioId: {}", portfolioId);
        
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

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오를 찾을 수 없습니다: " + portfolioId));
        
        if (!portfolio.userId().equals(userId)) {
            throw new ResourceNotFoundException("포트폴리오 삭제 권한이 없습니다: " + portfolioId);
        }

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

    private Rules createRulesFromRequest(com.fimatchplus.backend.portfolio.dto.UpdatePortfolioRequest.RulesRequest rulesRequest, String benchmarkCode) {
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

    private static List<Rules.RuleItem> getUpdateRuleItems(List<com.fimatchplus.backend.portfolio.dto.UpdatePortfolioRequest.RuleItemRequest> rulesRequest) {
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
                        null,
                        null,
                        holdingRequest.symbol(),
                        holdingRequest.shares(),
                        holdingRequest.currentPrice(),
                        holdingRequest.totalValue(),
                        holdingRequest.change(),
                        holdingRequest.changePercent(),
                        holdingRequest.weight(),
                        null,
                        null
                ))
                .toList();
    }

    /**
     * UpdatePortfolioRequest의 Holdings를 Holding 도메인 객체로 변환
     * (벤치마크 분석을 위해 임시 생성)
     */
    private List<Holding> convertUpdateHoldingsFromRequest(List<com.fimatchplus.backend.portfolio.dto.UpdatePortfolioRequest.HoldingRequest> holdingRequests) {
        if (holdingRequests == null || holdingRequests.isEmpty()) {
            return List.of();
        }
        
        return holdingRequests.stream()
                .map(holdingRequest -> new Holding(
                        null,
                        null,
                        holdingRequest.symbol(),
                        holdingRequest.shares(),
                        holdingRequest.currentPrice(),
                        holdingRequest.totalValue(),
                        holdingRequest.change(),
                        holdingRequest.changePercent(),
                        holdingRequest.weight(),
                        null,
                        null
                ))
                .toList();
    }
}
