package com.stockone19.backend.stock.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class KisTokenService {

    private static final String TOKEN_KEY = "kis-token";

    private final StringRedisTemplate redisTemplate;
    private final WebClient stockApiWebClient;

    @Value("${kis.stock.app-key}")
    private String appKey;

    @Value("${kis.stock.app-secret}")
    private String appSecret;

    public KisTokenService(StringRedisTemplate redisTemplate, @Qualifier("stockApiWebClient") WebClient stockApiWebClient) {
        this.redisTemplate = redisTemplate;
        this.stockApiWebClient = stockApiWebClient;
    }

    public String getAccessToken() {
        String cached = redisTemplate.opsForValue().get(TOKEN_KEY);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        return issueAndCacheToken();
    }

    private String issueAndCacheToken() {
        KisTokenResponse resp = stockApiWebClient
                .post()
                .uri("/oauth2/tokenP")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + "; charset=" + StandardCharsets.UTF_8)
                .body(BodyInserters.fromValue(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", appKey,
                        "appsecret", appSecret
                )))
                .retrieve()
                .bodyToMono(KisTokenResponse.class)
                .block();

        if (resp == null || resp.access_token() == null || resp.access_token().isEmpty()) {
            throw new IllegalStateException("Failed to issue KIS access token");
        }

        long ttlSeconds = (long) Math.max(0, Math.floor(resp.expires_in()));
        redisTemplate.opsForValue().set(TOKEN_KEY, resp.access_token(), Duration.ofSeconds(ttlSeconds));
        return resp.access_token();
    }
}


