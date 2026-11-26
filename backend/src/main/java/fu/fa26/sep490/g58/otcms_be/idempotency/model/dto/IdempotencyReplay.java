package fu.fa26.sep490.g58.otcms_be.idempotency.model.dto;

import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import java.time.Instant;
import java.util.UUID;

/** Stored replay data returned to application services. */
public record IdempotencyReplay(IdempotencyScope scope, UUID ownerId, String idempotencyKey,
        String requestFingerprint, UUID resourceId, Instant createdAt) {

    /** Convenience constructor for callers that only need replay validation and the referenced resource. */
    public IdempotencyReplay(String requestFingerprint, UUID resourceId, Instant createdAt) {
        this(null, null, null, requestFingerprint, resourceId, createdAt);
    }

    /** True when the replayed request carries the same payload fingerprint (hex, case-insensitive). */
    public boolean matches(String fingerprint) {
        return requestFingerprint != null && requestFingerprint.equalsIgnoreCase(fingerprint);
    }
}
