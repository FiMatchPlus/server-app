package com.stockone19.backend.stock.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisTokenResponse(
        String access_token,
        String token_type,
        double expires_in,
        String access_token_token_expired
) {}
