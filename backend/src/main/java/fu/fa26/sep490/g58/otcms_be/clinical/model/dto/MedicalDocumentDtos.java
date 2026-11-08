package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import jakarta.validation.constraints.*;
import java.math.BigInteger;
import tools.jackson.core.*;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class MedicalDocumentDtos {
    private MedicalDocumentDtos() { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadMetadata(@NotNull ClinicalMedicalDocumentType documentType, @NotBlank @Size(max = 255) String title) {
        @Override public String toString() { return "UploadMetadata[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateMetadata(@NotBlank @Size(max = 255) String title, @NotNull @Min(1) @JsonDeserialize(using = StrictVersion.class) Long version) {
        @Override public String toString() { return "UpdateMetadata[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReleaseRequest(@NotNull @Min(1) @JsonDeserialize(using = StrictVersion.class) Long version) { }
    public static class StrictVersion extends StdDeserializer<Long> {
        public StrictVersion() { super(Long.class); }
        @Override public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return context.reportInputMismatch(Long.class, "Version must be an integer");
            BigInteger value = parser.getBigIntegerValue();
            if (value.signum() < 1 || value.compareTo(BigInteger.valueOf(Long.MAX_VALUE - 1)) > 0)
                return context.reportInputMismatch(Long.class, "Invalid version");
            return value.longValue();
        }
    }
}
