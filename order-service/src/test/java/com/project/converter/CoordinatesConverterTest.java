package com.project.converter;

import com.project.dto.Coordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinatesConverterTest {

    private CoordinatesConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CoordinatesConverter();
    }

    @Test
    void convertToDatabaseColumn_validCoordinates_returnsJson() {
        Coordinates coords = new Coordinates(12.97, 77.59);
        String json = converter.convertToDatabaseColumn(coords);
        assertThat(json).contains("\"lat\":12.97").contains("\"lng\":77.59");
    }

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_validJson_returnsCoordinates() {
        String json = "{\"lat\":12.97,\"lng\":77.59}";
        Coordinates result = converter.convertToEntityAttribute(json);
        assertThat(result.lat()).isEqualTo(12.97);
        assertThat(result.lng()).isEqualTo(77.59);
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundtrip_coordinatesUnchangedAfterConversion() {
        Coordinates original = new Coordinates(13.05, 77.60);
        String json = converter.convertToDatabaseColumn(original);
        Coordinates result = converter.convertToEntityAttribute(json);
        assertThat(result).isEqualTo(original);
    }
}
