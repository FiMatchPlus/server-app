package com.fimatchplus.backend.user.controller;

import com.fimatchplus.backend.common.dto.ApiResponse;
import com.fimatchplus.backend.user.domain.User;
import com.fimatchplus.backend.user.dto.LoginRequest;
import com.fimatchplus.backend.user.dto.LoginResponse;
import com.fimatchplus.backend.user.dto.RegisterRequest;
import com.fimatchplus.backend.user.dto.RegisterResponse;
import com.fimatchplus.backend.user.service.AuthService;
import com.fimatchplus.backend.user.service.UserService;
import com.fimatchplus.backend.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register attempt for email: {}", request.getEmail());
        
        User user = userService.register(request);
        log.info("Register successful for user: {}", user.getEmail());
        
        RegisterResponse response = RegisterResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .gender(user.getGender())
                .createdAt(user.getCreatedAt())
                .build();
        
        return ApiResponse.success("회원가입이 완료되었습니다", response);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        
        LoginResponse response = authService.login(request);
        log.info("Login successful for user: {}", request.getEmail());
        
        return ApiResponse.success("로그인이 완료되었습니다", response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null && authService.isTokenValid(token)) {
            Long userId = jwtUtil.getUserIdFromToken(token);
            authService.logout(userId);
            log.info("Logout successful for user: {}", userId);
        }
        
        return ApiResponse.success("로그아웃이 완료되었습니다");
    }

    @GetMapping("/validate")
    public ApiResponse<Boolean> validateToken(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token == null) {
            return ApiResponse.success("토큰이 유효하지 않습니다", false);
        }
        
        boolean isValid = authService.isTokenValid(token);
        return ApiResponse.success("토큰 유효성 검증이 완료되었습니다", isValid);
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserInfo> getCurrentUser(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token == null || !authService.isTokenValid(token)) {
            return ApiResponse.error("인증되지 않은 사용자입니다");
        }
        
        User user = authService.getUserFromToken(token);
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
        
        return ApiResponse.success("사용자 정보를 조회했습니다", userInfo);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
