package fu.fa26.sep490.g58.otcms_be.common.api;

import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Safe, stable error envelope. Internal exception details must never be exposed here. */
public record ErrorResponse(Error error, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> fieldErrors) {

    public ErrorResponse(Error error) { this(error, List.of()); }

    public record FieldError(String field, String code) { }

    public static ErrorResponse withFieldErrors(String code, String message, String correlationId, List<FieldError> fields) {
        return new ErrorResponse(new Error(code, message, Instant.now(), correlationId, null), List.copyOf(fields));
    }

    public record Error(String code, String message, Instant timestamp, String correlationId,
            AdminMutationImpact impact) { }

    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(new Error(code, message, Instant.now(), correlationId, null));
    }

    public static ErrorResponse mutationBlocked(AdminMutationImpact impact, String correlationId) {
        return new ErrorResponse(new Error("RESOURCE_MUTATION_BLOCKED",
                "The resource cannot be changed while protected work depends on it",
                Instant.now(), correlationId, impact));
    }
}
