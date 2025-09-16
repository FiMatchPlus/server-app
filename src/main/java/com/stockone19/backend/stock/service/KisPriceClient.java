package com.stockone19.backend.stock.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
                .header("authorization", token)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010100")
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(KisQuoteResponse.class);

        KisQuoteResponse body = responseMono.block();
        return body;
    }

}


