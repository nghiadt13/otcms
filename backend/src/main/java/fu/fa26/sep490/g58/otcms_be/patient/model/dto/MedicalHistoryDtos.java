package fu.fa26.sep490.g58.otcms_be.patient.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import tools.jackson.core.*;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class MedicalHistoryDtos {
    private MedicalHistoryDtos() { }

    public record UpdateMedicalHistoryRequest(
            @NotNull @JsonDeserialize(using = StrictText.class) String conditions,
            @NotNull @JsonDeserialize(using = StrictText.class) String allergies,
            @NotNull @JsonDeserialize(using = StrictText.class) String procedures,
            @NotNull @JsonDeserialize(using = StrictText.class) String injuries,
            @NotNull @JsonDeserialize(using = StrictText.class) String orthopedicHistory,
            @NotNull @Min(0) @JsonDeserialize(using = StrictVersion.class) Long version) {
        @Override public String toString() { return "UpdateMedicalHistoryRequest[redacted]"; }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MedicalHistoryResponse(String conditions, String allergies, String procedures,
            String injuries, String orthopedicHistory, OffsetDateTime updatedAt, String updatedBy, long version) {
        public static MedicalHistoryResponse empty() {
            return new MedicalHistoryResponse("", "", "", "", "", null, null, 0);
        }
    }

    /** Scoped deserializers reject scalar coercion without changing shared Jackson behavior. */
    public static class StrictText extends StdDeserializer<String> {
        public StrictText() { super(String.class); }
        @Override public String deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING))
                return context.reportInputMismatch(String.class, "History text must be a JSON string");
            return parser.getString();
        }
    }
    public static class StrictVersion extends StdDeserializer<Long> {
        public StrictVersion() { super(Long.class); }
        @Override public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
                return context.reportInputMismatch(Long.class, "Version must be a JSON integer");
            BigInteger value = parser.getBigIntegerValue();
            if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
                return context.reportInputMismatch(Long.class, "Version is outside its allowed range");
            return value.longValue();
        }
    }
}
