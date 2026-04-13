package com.project.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.Coordinates;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CoordinatesConverter implements AttributeConverter<Coordinates, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Coordinates coordinates) {
        if (coordinates == null) return null;
        try {
            return MAPPER.writeValueAsString(coordinates);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize Coordinates to JSON", e);
        }
    }

    @Override
    public Coordinates convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, Coordinates.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize Coordinates from: " + dbData, e);
        }
    }
}
