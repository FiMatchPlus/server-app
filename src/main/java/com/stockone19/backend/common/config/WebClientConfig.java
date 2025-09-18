package com.stockone19.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient stockApiWebClient(
            WebClient.Builder builder,
            @Value("${kis.stock.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
    
    @Bean
    public WebClient backtestEngineWebClient(
            WebClient.Builder builder,
            @Value("${backtest.engine.url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
}


