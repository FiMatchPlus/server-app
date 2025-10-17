package com.fimatchplus.backend.user.service;

import com.fimatchplus.backend.user.domain.User;
import com.fimatchplus.backend.user.dto.LoginRequest;
import com.fimatchplus.backend.user.dto.LoginResponse;
import com.fimatchplus.backend.user.repository.UserRepository;
import com.fimatchplus.backend.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String JWT_PREFIX = "jwt:";
    private static final long JWT_EXPIRATION_DAYS = 3;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail());

        String redisKey = JWT_PREFIX + user.getId();
        redisTemplate.opsForValue().set(redisKey, accessToken, JWT_EXPIRATION_DAYS, TimeUnit.DAYS);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(JWT_EXPIRATION_DAYS * 24 * 60 * 60) // 초 단위
                .userInfo(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .build())
                .build();
    }

    public void logout(Long userId) {
        String redisKey = JWT_PREFIX + userId;
        redisTemplate.delete(redisKey);
        log.info("User {} logged out successfully", userId);
    }

    public boolean isTokenValid(String token) {
        if (!jwtUtil.validateToken(token)) {
            return false;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String redisKey = JWT_PREFIX + userId;
        String storedToken = redisTemplate.opsForValue().get(redisKey);
        
        return storedToken != null && storedToken.equals(token);
    }

    public User getUserFromToken(String token) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }
}
