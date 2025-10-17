package com.fimatchplus.backend.portfolio.service;

import com.fimatchplus.backend.portfolio.domain.Holding;
import com.fimatchplus.backend.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioCalculator {

    /**
     * 보유 종목 리스트와 가격 정보로부터 포트폴리오 총계 계산
     */
    public PortfolioTotals calculateTotals(List<Holding> holdings, Map<String, StockService.StockPriceInfo> priceMap) {
        double totalAssets = 0.0;
        double dailyChange = 0.0;

        for (Holding holding : holdings) {
            StockService.StockPriceInfo priceInfo = priceMap.get(holding.symbol());

            if (priceInfo != null) {
                double currentPrice = priceInfo.currentPrice();
                double currentValue = currentPrice * holding.shares();
                double holdingDailyChange = priceInfo.dailyChangePrice() * holding.shares();

                totalAssets += currentValue;
                dailyChange += holdingDailyChange;
            } else {
                log.warn("가격 정보를 찾을 수 없습니다: {}", holding.symbol());
                totalAssets += holding.totalValue();
            }
        }

        double dailyReturnPercent = totalAssets > 0 ? (dailyChange / totalAssets) * 100 : 0.0;
        return new PortfolioTotals(totalAssets, dailyChange, dailyReturnPercent);
    }

    /**
     * downside_deviation로 risk level 결정
     * 0.10 미만: LOW, 0.15 이상: HIGH, 0.10 이상 0.15 미만: MEDIUM
     */
    public String calculateRiskLevel(Double downsideDeviation) {
        if (downsideDeviation == null) {
            return "UNKNOWN";
        }
        
        if (downsideDeviation < 0.10) {
            return "LOW";
        } else if (downsideDeviation >= 0.15) {
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }

    /**
     * 포트폴리오 총계 정보를 담는 레코드
     */
    public record PortfolioTotals(
            double totalAssets,
            double dailyChange,
            double dailyReturnPercent
    ) {}
}
