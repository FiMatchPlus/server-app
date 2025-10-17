package com.fimatchplus.backend.user.controller;

import com.fimatchplus.backend.user.domain.User;
import com.fimatchplus.backend.user.dto.LoginRequest;
import com.fimatchplus.backend.user.dto.LoginResponse;
import com.fimatchplus.backend.user.dto.RegisterRequest;
import com.fimatchplus.backend.user.service.AuthService;
import com.fimatchplus.backend.user.service.UserService;
import com.fimatchplus.backend.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register attempt for email: {}", request.getEmail());
        
        User user = userService.register(request);
        log.info("Register successful for user: {}", user.getEmail());
        
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        
        LoginResponse response = authService.login(request);
        log.info("Login successful for user: {}", request.getEmail());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null && authService.isTokenValid(token)) {
            Long userId = jwtUtil.getUserIdFromToken(token);
            authService.logout(userId);
            log.info("Logout successful for user: {}", userId);
        }
        
        return ResponseEntity.ok().build();
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateToken(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token == null) {
            return ResponseEntity.ok(false);
        }
        
        boolean isValid = authService.isTokenValid(token);
        return ResponseEntity.ok(isValid);
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserInfo> getCurrentUser(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token == null || !authService.isTokenValid(token)) {
            return ResponseEntity.status(401).build();
        }
        
        User user = authService.getUserFromToken(token);
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
        
        return ResponseEntity.ok(userInfo);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
