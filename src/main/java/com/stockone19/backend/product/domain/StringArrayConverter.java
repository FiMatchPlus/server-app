package com.stockone19.backend.product.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * String 배열을 데이터베이스에 저장하기 위한 컨버터
 */
@Converter
public class StringArrayConverter implements AttributeConverter<String[], String> {

    private static final String DELIMITER = ",";

    @Override
    public String convertToDatabaseColumn(String[] attribute) {
        if (attribute == null || attribute.length == 0) {
            return "";
        }
        return String.join(DELIMITER, attribute);
    }

    @Override
    public String[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new String[0];
        }
        return dbData.split(DELIMITER);
    }
}

