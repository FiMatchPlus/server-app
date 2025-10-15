package com.fimatchplus.backend.user.domain;

import lombok.Getter;

@Getter
public enum Gender {
    M("남성"),
    F("여성");

    private final String description;

    Gender(String description) {
        this.description = description;
    }

}
