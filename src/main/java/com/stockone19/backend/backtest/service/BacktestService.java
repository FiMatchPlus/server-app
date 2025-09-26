package com.stockone19.backend.backtest.service;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.dto.*;
import com.stockone19.backend.backtest.repository.BacktestRepository;
import com.stockone19.backend.backtest.repository.BacktestRuleRepository;
import com.stockone19.backend.common.exception.ResourceNotFoundException;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BacktestService {

    private final BacktestRepository backtestRepository;
    private final PortfolioRepository portfolioRepository;
    private final BacktestRuleRepository backtestRuleRepository;

    /**
     * 백테스트 생성
     *
     * @param portfolioId 포트폴리오 ID
     * @param request     백테스트 생성 요청
     * @return 생성된 백테스트 정보
     */
    @Transactional
    public CreateBacktestResult createBacktest(Long portfolioId, CreateBacktestRequest request) {
        log.info("Creating backtest for portfolioId: {}, title: {}", portfolioId, request.title());

        // 포트폴리오 존재 확인
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + portfolioId));

        // 백테스트 생성 및 저장
        Backtest backtest = Backtest.create(
                portfolioId,
                request.title(),
                request.description(),
                request.startAt(),
                request.endAt()
        );

        Backtest savedBacktest = backtestRepository.save(backtest);

        // 백테스트 규칙이 있는 경우 MongoDB에 저장하고 ruleId 업데이트
        if (request.rules() != null && hasRules(request.rules())) {
            String ruleId = saveBacktestRules(savedBacktest.getId(), request.rules());
            
            // JPA 엔티티의 ruleId 업데이트
            savedBacktest.updateRuleId(ruleId);
            savedBacktest = backtestRepository.save(savedBacktest);
        }

        // 벤치마크 지수 설정 (필수 필드)
        savedBacktest.setBenchmarkCode(request.benchmarkCode());
        savedBacktest = backtestRepository.save(savedBacktest);
        log.info("벤치마크 지수 설정 완료: {} for backtestId: {}", request.benchmarkCode(), savedBacktest.getId());

        return new CreateBacktestResult(
                savedBacktest.getId(),
                savedBacktest.getTitle(),
                savedBacktest.getDescription(),
                savedBacktest.getRuleId(),
                savedBacktest.getStartAt(),
                savedBacktest.getEndAt(),
                savedBacktest.getBenchmarkCode()
        );
    }


    /**
     * 포트폴리오별 백테스트 목록 조회
     *
     * @param portfolioId 포트폴리오 ID
     * @return 백테스트 목록
     */
    public List<Backtest> getBacktestsByPortfolioId(Long portfolioId) {
        log.info("Getting backtests for portfolioId: {}", portfolioId);

        // 포트폴리오 존재 확인
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + portfolioId));

        return backtestRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId);
    }

    /**
     * 포트폴리오별 백테스트 상태만 조회
     *
     * @param portfolioId 포트폴리오 ID
     * @return 백테스트 ID와 상태 맵 (백테스트 ID -> 상태)
     */
    public Map<String, String> getBacktestStatusesByPortfolioId(Long portfolioId) {
        log.info("Getting backtest statuses for portfolioId: {}", portfolioId);

        // 포트폴리오 존재 확인
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + portfolioId));

        List<Backtest> backtests = backtestRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId);
        
        return backtests.stream()
                .collect(Collectors.toMap(
                    backtest -> String.valueOf(backtest.getId()),
                    backtest -> backtest.getStatus().name()
                ));
    }


    /**
     * 백테스트 상태 업데이트
     */
    @Transactional
    public void updateBacktestStatus(Long backtestId, BacktestStatus status) {
        log.info("Updating backtest status to {} for backtestId: {}", status, backtestId);
        
        Backtest backtest = backtestRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest not found with id: " + backtestId));
        backtest.updateStatus(status);
        backtestRepository.save(backtest);
        
        log.info("Successfully updated backtest status to {} for backtestId: {}", status, backtestId);
    }

    // ===== 규칙 관리 관련 private 메소드들 =====

    private boolean hasRules(CreateBacktestRequest.RulesRequest rules) {
        return (rules.stopLoss() != null && !rules.stopLoss().isEmpty()) ||
               (rules.takeProfit() != null && !rules.takeProfit().isEmpty()) ||
               (rules.memo() != null && !rules.memo().trim().isEmpty());
    }

    private String saveBacktestRules(Long backtestId, CreateBacktestRequest.RulesRequest rulesRequest) {
        List<BacktestRuleDocument.RuleItem> stopLossItems = convertToRuleItems(rulesRequest.stopLoss());
        List<BacktestRuleDocument.RuleItem> takeProfitItems = convertToRuleItems(rulesRequest.takeProfit());

        BacktestRuleDocument backtestRule = new BacktestRuleDocument(
                backtestId,
                rulesRequest.memo(),
                stopLossItems,
                takeProfitItems
        );

        BacktestRuleDocument savedRule = backtestRuleRepository.save(backtestRule);
        log.info("Saved backtest rule to MongoDB with id: {}", savedRule.getId());
        
        return savedRule.getId();
    }

    private List<BacktestRuleDocument.RuleItem> convertToRuleItems(List<CreateBacktestRequest.RuleItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        
        return items.stream()
                .map(item -> new BacktestRuleDocument.RuleItem(
                        item.category(),
                        item.threshold(),
                        item.description()
                ))
                .toList();
    }
}