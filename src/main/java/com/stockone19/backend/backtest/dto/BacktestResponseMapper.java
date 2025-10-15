package com.stockone19.backend.backtest.dto;

import com.stockone19.backend.backtest.domain.Backtest;
import com.stockone19.backend.backtest.exception.BacktestExecutionException;
import com.stockone19.backend.backtest.domain.PortfolioSnapshot;
import com.stockone19.backend.backtest.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 백테스트 도메인 객체를 응답 DTO로 변환하는 매퍼
 */
@Component
@RequiredArgsConstructor
public class BacktestResponseMapper {

    private final SnapshotRepository snapshotRepository;

    /**
     * Backtest 도메인 객체를 BacktestResponse로 변환
     */
    public BacktestResponse toResponse(Backtest backtest) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String period = backtest.getStartAt().format(formatter) + " ~ " + backtest.getEndAt().format(formatter);

        long executionTime = 0L;
        
        BacktestStatus status = backtest.getStatus();
        
        if (status == BacktestStatus.COMPLETED) {
            PortfolioSnapshot latestSnapshot = snapshotRepository.findLatestPortfolioSnapshotByBacktestId(backtest.getId());
            if (latestSnapshot != null && latestSnapshot.executionTime() != null) {
                executionTime = latestSnapshot.executionTime().longValue();
            }
        }
        
        return BacktestResponse.of(
                backtest.getId(),
                backtest.getTitle(),
                period,
                executionTime,
                backtest.getCreatedAt(),
                status
        );
    }

    /**
     * 백테스트 목록을 응답 목록으로 변환
     */
    public List<BacktestResponse> toResponseList(List<Backtest> backtests) {
        return backtests.stream()
                .map(this::toResponse)
                .toList();
    }



    
    /**
     * 백테스트 실행 예외를 클라이언트 친화적인 에러 응답으로 변환
     */
    public BacktestErrorResponse toErrorResponse(BacktestExecutionException exception) {
        if (!exception.hasStructuredError()) {
            return BacktestErrorResponse.createGeneralError(
                    "EXECUTION_ERROR",
                    exception.getMessage(),
                    null,
                    null
            );
        }
        
        BacktestServerErrorResponse serverError = exception.getErrorResponse();
        
        if ("MISSING_STOCK_PRICE_DATA".equals(serverError.error().errorType())) {
            return createMissingStockDataErrorResponse(serverError);
        }
        
        return BacktestErrorResponse.createGeneralError(
                serverError.error().errorType(),
                createFriendlyErrorMessage(serverError.error()),
                serverError.executionTime(),
                serverError.requestId()
        );
    }
    
    /**
     * 주가 데이터 누락 에러 응답 생성
     */
    private BacktestErrorResponse createMissingStockDataErrorResponse(BacktestServerErrorResponse serverError) {
        List<MissingStockData> missingData = serverError.error().missingData().stream()
                .map(data -> MissingStockData.builder()
                        .stockCode(data.stockCode())
                        .startDate(data.startDate())
                        .endDate(data.endDate())
                        .availableDateRange(data.availableDateRange())
                        .build())
                .collect(Collectors.toList());
        
        String friendlyMessage = createMissingDataFriendlyMessage(
                serverError.error().missingStocksCount(),
                serverError.error().requestedPeriod()
        );
        
        return BacktestErrorResponse.createMissingStockDataError(
                friendlyMessage,
                missingData,
                serverError.error().requestedPeriod(),
                serverError.error().totalStocks(),
                serverError.error().missingStocksCount(),
                serverError.executionTime(),
                serverError.requestId()
        );
    }
    
    /**
     * 주가 데이터 누락 시 친절한 메시지 생성
     */
    private String createMissingDataFriendlyMessage(int missingStocksCount, String requestedPeriod) {
        return String.format(
                "백테스트 실행에 필요한 주가 데이터가 부족합니다. " +
                "%d개 종목의 %s 기간 데이터를 찾을 수 없어 백테스트를 진행할 수 없습니다. " +
                "다른 기간을 선택하시거나 해당 종목을 제외하고 다시 시도해주세요.",
                missingStocksCount,
                requestedPeriod
        );
    }
    
    /**
     * 서버 에러를 친절한 메시지로 변환
     */
    private String createFriendlyErrorMessage(BacktestServerErrorResponse.ErrorInfo error) {
        return switch (error.errorType()) {
            case "INVALID_DATE_RANGE" -> 
                "백테스트 기간이 올바르지 않습니다. 시작일과 종료일을 다시 확인해주세요.";
            case "INSUFFICIENT_DATA" -> 
                "백테스트 실행에 필요한 데이터가 부족합니다. 다른 기간이나 종목을 선택해주세요.";
            case "PORTFOLIO_EMPTY" -> 
                "포트폴리오가 비어있습니다. 종목을 추가한 후 다시 시도해주세요.";
            case "SERVER_ERROR" -> 
                "서버에서 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
            default -> 
                error.message() != null ? error.message() : "백테스트 실행 중 오류가 발생했습니다.";
        };
    }
}
