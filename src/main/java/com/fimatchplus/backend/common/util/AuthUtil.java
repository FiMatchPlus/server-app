package com.fimatchplus.backend.common.util;

import com.fimatchplus.backend.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthUtil {

    private final String jwtSecret;
    private final long jwtExpiration;

    public AuthUtil(@Value("${jwt.secret}") String jwtSecret,
                    @Value("${jwt.expiration}") long jwtExpiration) {
        this.jwtSecret = jwtSecret;
        this.jwtExpiration = jwtExpiration;
    }

    public Long getUserIdFromRequest(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token == null) {
            throw new IllegalArgumentException("JWT 토큰이 없습니다");
        }

        try {
            JwtUtil jwtUtil = new JwtUtil(jwtSecret, jwtExpiration);
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.error("JWT 토큰에서 userId 추출 실패: {}", e.getMessage());
            throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다");
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
