package fu.fa26.sep490.g58.otcms_be.imaging.service;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/** Streaming service input. The caller owns and closes the body stream. */
public record ImagingUploadCommand(UUID actorAccountId, UUID requestId, UUID attemptId,
        String idempotencyKey, String contentType, long contentLength, InputStream content) {
    public ImagingUploadCommand {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(attemptId, "attemptId");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        contentType = requireText(contentType, "contentType");
        if (!idempotencyKey.matches("[\\x21-\\x7E]{1,128}")) {
            throw new IllegalArgumentException("idempotencyKey must be 1-128 visible ASCII characters");
        }
        if (contentLength < -1) throw new IllegalArgumentException("contentLength must be -1 or non-negative");
        Objects.requireNonNull(content, "content");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
