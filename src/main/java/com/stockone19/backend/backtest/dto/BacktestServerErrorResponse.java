package com.stockone19.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백테스트 서버로부터 받는 에러 응답
 */
@Builder
public record BacktestServerErrorResponse(
        boolean success,
        ErrorInfo error,
        @JsonProperty("execution_time") Double executionTime,
        @JsonProperty("request_id") String requestId
) {
    
    @Builder
    public record ErrorInfo(
            @JsonProperty("error_type") String errorType,
            String message,
            @JsonProperty("missing_data") List<MissingDataInfo> missingData,
            @JsonProperty("requested_period") String requestedPeriod,
            @JsonProperty("total_stocks") Integer totalStocks,
            @JsonProperty("missing_stocks_count") Integer missingStocksCount,
            LocalDateTime timestamp
    ) {}
    
    @Builder
    public record MissingDataInfo(
            @JsonProperty("stock_code") String stockCode,
            @JsonProperty("start_date") String startDate,
            @JsonProperty("end_date") String endDate,
            @JsonProperty("available_date_range") String availableDateRange
    ) {}
}
