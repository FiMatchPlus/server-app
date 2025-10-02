package com.stockone19.backend.common.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient stockApiWebClient(
            WebClient.Builder builder,
            @Value("${kis.stock.base-url}") String baseUrl
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)  // 연결 타임아웃 5초
                .responseTimeout(Duration.ofSeconds(10))             // 응답 타임아웃 10초
                .doOnConnected(conn -> 
                    conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))   // 읽기 타임아웃 10초
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))   // 쓰기 타임아웃 5초
                );

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
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
    
    @Bean
    public WebClient portfolioAnalysisEngineWebClient(
            WebClient.Builder builder,
            @Value("${portfolio.engine.url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
}


