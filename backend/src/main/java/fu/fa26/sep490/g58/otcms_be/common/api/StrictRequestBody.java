package fu.fa26.sep490.g58.otcms_be.common.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Shared request-body base that rejects unsupported JSON properties. */
public abstract class StrictRequestBody {
    private final Set<String> unsupportedFields = new HashSet<>();

    @JsonAnySetter
    public void captureUnsupportedField(String name, Object value) {
        unsupportedFields.add(name);
    }

    @AssertTrue(message = "Unsupported request field")
    @Schema(hidden = true)
    public boolean isRequestFieldsSupported() {
        return unsupportedFields.isEmpty();
    }

    protected String strictText(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Expected text value");
        return value.textValue();
    }

    protected UUID strictUuid(JsonNode value) {
        String text = strictText(value);
        if (text == null) return null;
        UUID parsed = UUID.fromString(text);
        if (!parsed.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException("Expected UUID value");
        return parsed;
    }

    protected OffsetDateTime strictOffsetDateTime(JsonNode value) {
        String text = strictText(value);
        return text == null ? null : OffsetDateTime.parse(text);
    }
}
