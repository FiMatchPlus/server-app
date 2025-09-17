package com.stockone19.backend.backtest.dto;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.portfolio.domain.PortfolioSnapshot;
import com.stockone19.backend.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 백테스트 도메인 객체를 응답 DTO로 변환하는 매퍼
 */
@Component
@RequiredArgsConstructor
public class BacktestResponseMapper {

    private final PortfolioRepository portfolioRepository;

    /**
     * Backtest 도메인 객체를 BacktestResponse로 변환
     */
    public BacktestResponse toResponse(Backtest backtest, Long portfolioId) {
        // 백테스트 기간 문자열 생성
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String period = backtest.getStartAt().format(formatter) + " ~ " + backtest.getEndAt().format(formatter);
        
        // 실행 시간 계산 (현재는 임시로 0 설정)
        Long executionTime = 0L;
        
        // 백테스트 상태 결정
        BacktestStatus status = determineBacktestStatus(portfolioId);
        
        if (status == BacktestStatus.COMPLETED) {
            // 완료된 경우 지표와 일별 수익률 포함
            BacktestMetrics metrics = generateMockMetrics(); // 임시 지표
            List<BacktestResponse.DailyReturn> dailyReturns = generateDailyReturns(portfolioId);
            
            return BacktestResponse.ofCompleted(
                    backtest.getId(),
                    backtest.getTitle(),
                    period,
                    executionTime,
                    backtest.getCreatedAt(),
                    status,
                    metrics,
                    dailyReturns
            );
        } else {
            // 진행 중이거나 생성됨 상태
            return BacktestResponse.of(
                    backtest.getId(),
                    backtest.getTitle(),
                    period,
                    executionTime,
                    backtest.getCreatedAt(),
                    status
            );
        }
    }

    /**
     * 백테스트 목록을 응답 목록으로 변환
     */
    public List<BacktestResponse> toResponseList(List<Backtest> backtests, Long portfolioId) {
        return backtests.stream()
                .map(backtest -> toResponse(backtest, portfolioId))
                .toList();
    }

    private BacktestStatus determineBacktestStatus(Long portfolioId) {
        // portfolio_snapshots에서 portfolio_id로 레코드를 찾을 수 있는 경우 completed
        boolean hasSnapshots = portfolioRepository.existsSnapshotByPortfolioId(portfolioId);
        
        if (hasSnapshots) {
            return BacktestStatus.COMPLETED;
        } else {
            // 현재는 간단히 CREATED 상태로 반환
            // 실제로는 백테스트 실행 상태를 추적하는 로직이 필요
            return BacktestStatus.CREATED;
        }
    }

    private BacktestMetrics generateMockMetrics() {
        // 임시 지표 데이터 - 실제 구현 시 백테스트 결과에서 계산
        return BacktestMetrics.of(
                15.5,   // totalReturn
                12.3,   // annualizedReturn
                18.7,   // volatility
                0.65,   // sharpeRatio
                -8.2,   // maxDrawdown
                0.65,   // winRate
                1.2     // profitLossRatio
        );
    }

    private List<BacktestResponse.DailyReturn> generateDailyReturns(Long portfolioId) {
        // portfolio_snapshots에서 일별 수익률 데이터 조회
        List<PortfolioSnapshot> snapshots = portfolioRepository.findSnapshotsByPortfolioId(portfolioId);
        
        return snapshots.stream()
                .map(snapshot -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("return", snapshot.getDailyReturn());
                    data.put("value", snapshot.currentValue());
                    data.put("change", snapshot.getDailyChange());
                    
                    return new BacktestResponse.DailyReturn(
                            snapshot.createdAt().toLocalDate().toString(),
                            data
                    );
                })
                .toList();
    }
}
