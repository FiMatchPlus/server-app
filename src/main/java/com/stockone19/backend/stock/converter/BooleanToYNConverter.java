package com.stockone19.backend.stock.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class BooleanToYNConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return "Y".equals(dbData);
    }
}
