package com.stockone19.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String status,
        String message,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        T data
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, Instant.now(), data);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>("success", message, Instant.now(), null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("error", message, Instant.now(), null);
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>("error", message, Instant.now(), data);
    }
}

