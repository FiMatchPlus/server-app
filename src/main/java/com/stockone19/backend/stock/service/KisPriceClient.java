package com.stockone19.backend.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class KisPriceClient {

    @Value("${kis.stock.base-url}")
    private String baseUrl;
    @Value("${kis.stock.app-key}")
    private String appKey;
    @Value("${kis.stock.app-secret}")
    private String appSecret;

    private final WebClient webClient;

    private final KisTokenService kisTokenService;

    public KisPriceClient(@Qualifier("stockApiWebClient") WebClient webClient, KisTokenService kisTokenService) {
        this.webClient = webClient;
        this.kisTokenService = kisTokenService;
    }

    public KisQuoteResponse fetchQuote(String ticker) {
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

        KisQuoteResponse body = responseMono.block();
        return body;
    }

    public KisMultiPriceResponse fetchMultiPrice(List<String> tickers) {
        if (tickers.isEmpty()) {
            return new KisMultiPriceResponse("0", "00000", "정상처리", List.of());
        }

        String token = kisTokenService.getAccessToken();
        
        // 최대 30개까지 처리 가능하므로 30개씩 나누어 처리
        if (tickers.size() > 30) {
            throw new IllegalArgumentException("한 번에 최대 30개 종목까지만 조회 가능합니다. 현재: " + tickers.size());
        }

        var uriBuilder = webClient.get()
                .uri(uriBuilderParam -> {
                    var builder = uriBuilderParam.path("/uapi/domestic-stock/v1/quotations/intstock-multprice");
                    
                    // 각 종목에 대해 쿼리 파라미터 추가
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

        KisMultiPriceResponse body = responseMono.block();
        return body;
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


