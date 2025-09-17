package com.stockone19.backend.stock.controller;

import com.stockone19.backend.common.dto.ApiResponse;
import com.stockone19.backend.stock.dto.StockDetailResponse;
import com.stockone19.backend.stock.dto.StockPriceResponse;
import com.stockone19.backend.stock.dto.StockSearchResponse;
import com.stockone19.backend.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/stocks")
public class StockController {

    private final StockService stockService;

    /**
     * 여러 종목의 현재가 정보 조회
     * <ul>
     *     <li>종목 코드 리스트를 받아 해당 종목들의 현재가 정보를 반환</li>
     * </ul>
     * */
    @GetMapping
    public ApiResponse<StockPriceResponse> getStockPrices(
            @RequestParam("codes") List<String> codes) {
        log.info("GET /api/stocks - codes: {}", codes);

        StockPriceResponse response = stockService.getStockPrices(codes);
        return ApiResponse.success("여러 종목들의 현재가를 조회합니다", response);
    }

    /**
     * 단일 종목의 상세 정보 조회
     * <ul>
     *     <li>종목 코드와 차트 간격(interval)을 받아 해당 종목의 상세 정보를 반환</li>
     *     <li>차트 간격(interval) 기본값은 '1d'</li>
     * </ul>
     * */
    @GetMapping("/detail")
    public ApiResponse<StockDetailResponse> getStockDetail(
            @RequestParam("codes") String codes,
            @RequestParam(value = "intervals", defaultValue = "1d") String intervals) {
        log.info("GET /api/stocks/detail - codes: {}, intervals: {}", codes, intervals);

        StockDetailResponse response = stockService.getStockDetail(codes, intervals);
        return ApiResponse.success("단일 종목의 정보를 조회합니다", response);
    }

    /**
     * 특정 종목의 차트 데이터 조회
     * <ul>
     *     <li>종목 ID와 시간 간격을 받아 해당 종목의 차트 데이터를 반환</li>
     *     <li>시간 간격: '1m'(1분), '1d'(1일), '1W'(1주), '1Y'(1년)</li>
     *     <li>시작일과 종료일을 지정하여 특정 기간의 데이터 조회 가능</li>
     * </ul>
     * */
    @GetMapping("/chart")
    public ApiResponse<List<StockDetailResponse.ChartData>> getChartData(
            @RequestParam("stockId") String stockId,
            @RequestParam(value = "interval", defaultValue = "1d") String interval,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {

        // 기본값 설정
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(31)
                    .toLocalDate()
                    .withDayOfMonth(1)
                    .atStartOfDay();
        }

        log.info("GET /api/stocks/chart - stockId: {}, interval: {}, startDate: {}, endDate: {}, limit: {}", stockId, interval, startDate, endDate, limit);

        List<StockDetailResponse.ChartData> chartData = stockService.getChartData(stockId, interval, startDate, endDate, limit);
        return ApiResponse.success("차트데이터를 조회합니다", chartData);
    }

    /**
     * 종목 이름 또는 코드로 검색
     * <ul>
     *     <li>종목명, 영문명, 티커로 검색 가능</li>
     *     <li>검색 결과는 정확도 순으로 정렬 (정확한 티커 매치 > 종목명 매치 > 영문명 매치 > 티커 부분 매치 > 종목명 부분 매치 > 영문명 부분 매치)</li>
     *     <li>limit 파라미터로 검색 결과 수 제한 가능 (기본값: 20)</li>
     * </ul>
     * */
    @GetMapping("/search")
    public ApiResponse<StockSearchResponse> searchStocks(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.info("GET /api/stocks/search - keyword: {}, limit: {}", keyword, limit);

        StockSearchResponse response = stockService.searchStocks(keyword, limit);
        return ApiResponse.success("종목 검색이 완료되었습니다", response);
    }

    /**
     * 단일 종목 현재가 조회
     * <ul>
     *     <li>종목 선택 카드</li>
     * </ul>
     */
    @GetMapping("/now")
    public ApiResponse<StockPriceResponse> getCurrentPriceNow(
            @RequestParam("code") String code
    ) {
        log.info("GET /api/stocks/now - code: {}", code);
        StockPriceResponse response = stockService.getCurrentPriceForSingle(code);
        return ApiResponse.success("단일 종목의 현재가를 조회합니다", response);
    }
}
