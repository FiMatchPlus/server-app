package com.stockone19.backend.stock.service;

public record KisTokenResponse(
        String access_token,
        String token_type,
        double expires_in,
        String access_token_token_expired
) {}


