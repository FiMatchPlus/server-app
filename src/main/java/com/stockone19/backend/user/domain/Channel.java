package com.stockone19.backend.user.domain;

import lombok.Getter;

@Getter
public enum Channel {
    EMAIL("이메일"),
    SMS("SMS"),
    KAKAOTALK("카카오톡");

    private final String description;

    Channel(String description) {
        this.description = description;
    }

}
