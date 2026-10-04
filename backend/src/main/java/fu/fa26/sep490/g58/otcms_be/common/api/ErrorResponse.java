package fu.fa26.sep490.g58.otcms_be.common.api;

import java.time.Instant;

/** Safe, stable error envelope. Internal exception details must never be exposed here. */
public record ErrorResponse(Error error) {

    public record Error(String code, String message, Instant timestamp, String correlationId) { }

    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(new Error(code, message, Instant.now(), correlationId));
    }
}
