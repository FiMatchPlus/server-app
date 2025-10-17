package com.fimatchplus.backend.user.dto;

import com.fimatchplus.backend.user.domain.Gender;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RegisterResponse {
    
    private Long id;
    private String name;
    private String email;
    private String mobile;
    private Gender gender;
    private LocalDateTime createdAt;
}
