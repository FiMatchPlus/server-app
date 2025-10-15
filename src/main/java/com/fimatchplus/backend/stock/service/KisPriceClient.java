package com.fimatchplus.backend.stock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KisPriceClient {

    private static final String PRICE_CACHE_PREFIX = "kis:price:";
    private static final Duration PRICE_CACHE_TTL = Duration.ofMinutes(1);

    @Value("${kis.stock.base-url}")
    private String baseUrl;
    @Value("${kis.stock.app-key}")
    private String appKey;
    @Value("${kis.stock.app-secret}")
    private String appSecret;

    private final WebClient webClient;
    private final KisTokenService kisTokenService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public KisPriceClient(
            @Qualifier("stockApiWebClient") WebClient webClient, 
            KisTokenService kisTokenService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.kisTokenService = kisTokenService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public KisQuoteResponse fetchQuote(String ticker) {
        KisQuoteResponse cached = getCachedPrice(ticker);
        if (cached != null) {
            log.debug("Cache hit for ticker: {}", ticker);
            return cached;
        }
        
        log.debug("Cache miss for ticker: {}, calling KIS API", ticker);
        String token = kisTokenService.getAccessToken();

        Mono<KisQuoteResponse> responseMono = webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", ticker)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + "; charset=UTF-8")
                .header("authorization", "Bearer " + token)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010100")
                .header("custtype", "P")
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> logAndExtractError(response))
                .bodyToMono(KisQuoteResponse.class);

        KisQuoteResponse response = responseMono.block();
        
        if (response != null) {
            cachePrice(ticker, response);
        }
        
        return response;
    }

    public KisMultiPriceResponse fetchMultiPrice(List<String> tickers) {
        if (tickers.isEmpty()) {
            return new KisMultiPriceResponse("0", "00000", "정상처리", List.of());
        }
        
        if (tickers.size() > 30) {
            throw new IllegalArgumentException("한 번에 최대 30개 종목까지만 조회 가능합니다. 현재: " + tickers.size());
        }

        Map<String, KisQuoteResponse> cachedPrices = new HashMap<>();
        List<String> missedTickers = new ArrayList<>();
        
        for (String ticker : tickers) {
            KisQuoteResponse cached = getCachedPrice(ticker);
            if (cached != null) {
                cachedPrices.put(ticker, cached);
            } else {
                missedTickers.add(ticker);
            }
        }
        
        log.debug("Cache hit: {}/{}, missed: {}", cachedPrices.size(), tickers.size(), missedTickers.size());
        
        if (missedTickers.isEmpty()) {
            return buildMultiPriceResponseFromCache(cachedPrices);
        }
        
        KisMultiPriceResponse apiResponse = callKisMultiPriceApi(missedTickers);
        
        if (apiResponse != null && apiResponse.output() != null) {
            for (var priceData : apiResponse.output()) {
                cacheMultiPriceItem(priceData);
            }
        }
        
        return mergeMultiPriceResponses(cachedPrices, apiResponse);
    }
    
    private KisMultiPriceResponse callKisMultiPriceApi(List<String> tickers) {
        String token = kisTokenService.getAccessToken();

        var uriBuilder = webClient.get()
                .uri(uriBuilderParam -> {
                    var builder = uriBuilderParam.path("/uapi/domestic-stock/v1/quotations/intstock-multprice");
                    
                    for (int i = 0; i < tickers.size(); i++) {
                        builder.queryParam("FID_COND_MRKT_DIV_CODE_" + (i + 1), "J");
                        builder.queryParam("FID_INPUT_ISCD_" + (i + 1), tickers.get(i));
                    }
                    
                    return builder.build();
                });

        Mono<KisMultiPriceResponse> responseMono = uriBuilder
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + "; charset=UTF-8")
                .header("authorization", "Bearer " + token)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST11300006")
                .header("custtype", "P")
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> logAndExtractMultiPriceError(response))
                .bodyToMono(KisMultiPriceResponse.class);

        return responseMono.block();
    }

    private KisQuoteResponse getCachedPrice(String ticker) {
        try {
            String key = PRICE_CACHE_PREFIX + ticker;
            String cached = redisTemplate.opsForValue().get(key);
            
            if (cached == null || cached.isEmpty()) {
                return null;
            }
            
            return objectMapper.readValue(cached, KisQuoteResponse.class);
        } catch (Exception e) {
            log.warn("Failed to get cached price for ticker: {}, error: {}", ticker, e.getMessage());
            return null;
        }
    }
    
    private void cachePrice(String ticker, KisQuoteResponse response) {
        try {
            String key = PRICE_CACHE_PREFIX + ticker;
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, PRICE_CACHE_TTL);
            log.debug("Cached price for ticker: {}", ticker);
        } catch (Exception e) {
            log.warn("Failed to cache price for ticker: {}, error: {}", ticker, e.getMessage());
        }
    }
    
    private void cacheMultiPriceItem(KisMultiPriceResponse.ResponseBodyOutput item) {
        try {
            String ticker = item.interShrnIscd();
            if (ticker == null || ticker.isEmpty()) {
                return;
            }
            
            Map<String, Object> outputMap = new HashMap<>();
            outputMap.put("stck_shrn_iscd", ticker);
            outputMap.put("stck_prpr", item.inter2Prpr());
            outputMap.put("prdy_vrss", item.inter2PrdyVrss());
            outputMap.put("prdy_vrss_sign", item.prdyVrssSign());
            outputMap.put("prdy_ctrt", item.prdyCtrt());
            outputMap.put("stck_oprc", item.inter2Oprc());
            outputMap.put("stck_hgpr", item.inter2Hgpr());
            outputMap.put("stck_lwpr", item.inter2Lwpr());
            outputMap.put("acml_vol", item.acmlVol());
            outputMap.put("acml_tr_pbmn", item.acmlTrPbmn());
            
            KisQuoteResponse quoteResponse = new KisQuoteResponse(outputMap);
            cachePrice(ticker, quoteResponse);
        } catch (Exception e) {
            log.warn("Failed to cache multi-price item: {}", e.getMessage());
        }
    }
    
    private KisMultiPriceResponse buildMultiPriceResponseFromCache(Map<String, KisQuoteResponse> cachedPrices) {
        List<KisMultiPriceResponse.ResponseBodyOutput> outputList = cachedPrices.entrySet().stream()
                .map(entry -> {
                    String ticker = entry.getKey();
                    Map<String, Object> output = entry.getValue().output();
                    
                    return new KisMultiPriceResponse.ResponseBodyOutput(
                            ticker,
                            (String) output.get("stck_kor_isnm"),
                            (String) output.get("stck_prpr"),
                            (String) output.get("prdy_vrss"),
                            (String) output.get("prdy_vrss_sign"),
                            (String) output.get("prdy_ctrt"),
                            (String) output.get("stck_sdpr"),
                            (String) output.get("stck_oprc"),
                            (String) output.get("stck_hgpr"),
                            (String) output.get("stck_lwpr"),
                            (String) output.get("acml_vol"),
                            (String) output.get("acml_tr_pbmn")
                    );
                })
                .collect(Collectors.toList());
        
        return new KisMultiPriceResponse("0", "00000", "정상처리(캐시)", outputList);
    }
    
    private KisMultiPriceResponse mergeMultiPriceResponses(
            Map<String, KisQuoteResponse> cachedPrices, 
            KisMultiPriceResponse apiResponse) {
        
        List<KisMultiPriceResponse.ResponseBodyOutput> mergedOutput = new ArrayList<>();
        
        if (apiResponse != null && apiResponse.output() != null) {
            mergedOutput.addAll(apiResponse.output());
        }
        
        for (Map.Entry<String, KisQuoteResponse> entry : cachedPrices.entrySet()) {
            String ticker = entry.getKey();
            Map<String, Object> output = entry.getValue().output();
            
            KisMultiPriceResponse.ResponseBodyOutput cachedItem = 
                    new KisMultiPriceResponse.ResponseBodyOutput(
                            ticker,
                            (String) output.get("stck_kor_isnm"),
                            (String) output.get("stck_prpr"),
                            (String) output.get("prdy_vrss"),
                            (String) output.get("prdy_vrss_sign"),
                            (String) output.get("prdy_ctrt"),
                            (String) output.get("stck_sdpr"),
                            (String) output.get("stck_oprc"),
                            (String) output.get("stck_hgpr"),
                            (String) output.get("stck_lwpr"),
                            (String) output.get("acml_vol"),
                            (String) output.get("acml_tr_pbmn")
                    );
            mergedOutput.add(cachedItem);
        }
        
        String rtCd = apiResponse != null ? apiResponse.rtCd() : "0";
        String msgCd = apiResponse != null ? apiResponse.msgCd() : "00000";
        String msg1 = apiResponse != null ? apiResponse.msg1() : "정상처리";
        
        return new KisMultiPriceResponse(rtCd, msgCd, msg1, mergedOutput);
    }

    private Mono<Throwable> logAndExtractError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    log.error("KIS inquire-price error: status={}, body={}", response.statusCode(), body);
                    return new RuntimeException("KIS API error: " + response.statusCode());
                });
    }

    private Mono<Throwable> logAndExtractMultiPriceError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    log.error("KIS multi-price error: status={}, body={}", response.statusCode(), body);
                    return new RuntimeException("KIS Multi-Price API error: " + response.statusCode());
                });
    }

}
