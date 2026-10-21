package fu.fa26.sep490.g58.otcms_be.common.api;

import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import java.time.Instant;

/** Safe, stable error envelope. Internal exception details must never be exposed here. */
public record ErrorResponse(Error error) {

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
