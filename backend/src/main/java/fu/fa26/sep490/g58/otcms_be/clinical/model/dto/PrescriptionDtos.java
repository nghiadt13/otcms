package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import com.fasterxml.jackson.annotation.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class PrescriptionDtos {
    private PrescriptionDtos() { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DraftRequest(@NotEmpty List<@NotNull @Valid ItemRequest> items,
            @NotNull @Min(0) @JsonDeserialize(using = StrictVersion.class) Long version) {
        @Override public String toString() { return "DraftRequest[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemRequest(@NotBlank @Size(max = 255) String medicine, @NotBlank @Size(max = 255) String dose,
            @NotBlank @Size(max = 255) String frequency, @NotBlank @Size(max = 2000) String usage) {
        @Override public String toString() { return "ItemRequest[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssuanceRequest(@NotNull @Min(1) @JsonDeserialize(using = StrictVersion.class) Long version) { }
    public record ItemResponse(int lineNumber, String medicine, String dose, String frequency, String usage) {
        @Override public String toString() { return "ItemResponse[redacted]"; }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PrescriptionResponse(UUID id, UUID visitId, UUID patientProfileId, ClinicalPrescriptionStatus status,
            Instant issuedAt, long version, List<ItemResponse> items) {
        public PrescriptionResponse { items = List.copyOf(items); }
        @Override public String toString() { return "PrescriptionResponse[redacted]"; }
    }
    /** Scoped integer validation, following Patient medical-history's established transport convention. */
    public static class StrictVersion extends StdDeserializer<Long> {
        public StrictVersion() { super(Long.class); }
        @Override public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return context.reportInputMismatch(Long.class, "Version must be an integer");
            BigInteger value = parser.getBigIntegerValue();
            if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(Long.MAX_VALUE - 1)) > 0)
                return context.reportInputMismatch(Long.class, "Invalid version");
            return value.longValue();
        }
    }
}
