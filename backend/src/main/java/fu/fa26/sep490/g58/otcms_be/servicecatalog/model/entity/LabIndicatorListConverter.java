package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class LabIndicatorListConverter implements AttributeConverter<List<LabIndicator>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<LabIndicator>> TYPE = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<LabIndicator> value) {
        try {
            return JSON.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not encode laboratory indicators", exception);
        }
    }

    @Override
    public List<LabIndicator> convertToEntityAttribute(String value) {
        try {
            return value == null || value.isBlank() ? List.of() : JSON.readValue(value, TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not decode laboratory indicators", exception);
        }
    }
}
