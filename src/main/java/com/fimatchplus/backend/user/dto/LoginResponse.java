package com.fimatchplus.backend.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private UserInfo userInfo;
    
    @Getter
    @Builder
    public static class UserInfo {
        private Long id;
        private String name;
        private String email;
    }
}
