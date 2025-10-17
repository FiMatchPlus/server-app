package com.fimatchplus.backend.user.filter;

import com.fimatchplus.backend.user.domain.User;
import com.fimatchplus.backend.user.repository.UserRepository;
import com.fimatchplus.backend.user.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private static final String JWT_PREFIX = "jwt:";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        
        // 인증이 필요 없는 경로들
        if (isPublicPath(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractTokenFromRequest(request);
            
            if (token != null) {
                JwtUtil jwtUtil = new JwtUtil(jwtSecret, jwtExpiration);
                
                if (jwtUtil.validateToken(token) && isTokenValidInRedis(token)) {
                    User user = getUserFromToken(token, jwtUtil);
                    
                    if (user != null) {
                        UsernamePasswordAuthenticationToken authentication = 
                            new UsernamePasswordAuthenticationToken(
                                user, null, new ArrayList<>()
                            );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("User {} authenticated successfully", user.getEmail());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isPublicPath(String requestURI) {
        return requestURI.startsWith("/api/auth/register") ||
               requestURI.startsWith("/api/auth/login") ||
               requestURI.startsWith("/api/auth/validate") ||
               requestURI.startsWith("/actuator") ||
               requestURI.startsWith("/swagger") ||
               requestURI.startsWith("/v3/api-docs") ||
               requestURI.equals("/favicon.ico");
    }
    
    private boolean isTokenValidInRedis(String token) {
        try {
            JwtUtil jwtUtil = new JwtUtil(jwtSecret, jwtExpiration);
            Long userId = jwtUtil.getUserIdFromToken(token);
            String redisKey = JWT_PREFIX + userId;
            String storedToken = redisTemplate.opsForValue().get(redisKey);
            
            return storedToken != null && storedToken.equals(token);
        } catch (Exception e) {
            return false;
        }
    }
    
    private User getUserFromToken(String token, JwtUtil jwtUtil) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            Optional<User> userOpt = userRepository.findById(userId);
            return userOpt.orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
