package fu.fa26.sep490.g58.otcms_be.common.idempotency;

import java.time.Instant;
import java.util.UUID;

/** A previously recorded request: the caller compares {@code requestFingerprint}, then reloads the business row by {@code resourceId}. */
public record IdempotencyReplay(String requestFingerprint, UUID resourceId, Instant createdAt) {

    /** True when the replayed request carries the same payload fingerprint (hex, case-insensitive). */
    public boolean matches(String fingerprint) {
        return requestFingerprint != null && requestFingerprint.equalsIgnoreCase(fingerprint);
    }
}
