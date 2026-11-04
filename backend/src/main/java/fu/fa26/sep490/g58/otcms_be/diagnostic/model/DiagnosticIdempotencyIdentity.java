package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import java.util.Objects;
import java.util.UUID;

/** Operation-scoped idempotency identity; durable replay storage is added in T011. */
public final class DiagnosticIdempotencyIdentity {
    private final UUID actorAccountId;
    private final String systemSourceId;
    private final UUID visitId;
    private final UUID requestId;
    private final DiagnosticAction action;
    private final String key;
    private final String payloadFingerprint;

    public DiagnosticIdempotencyIdentity(UUID actorAccountId, String systemSourceId, UUID visitId,
            UUID requestId, DiagnosticAction action, String key, String payloadFingerprint) {
        if ((actorAccountId == null) == (systemSourceId == null)) {
            throw new IllegalArgumentException("exactly one authenticated actor or system source is required");
        }
        this.actorAccountId = actorAccountId;
        this.systemSourceId = systemSourceId == null ? null : requireText(systemSourceId, "systemSourceId");
        this.visitId = Objects.requireNonNull(visitId, "visitId");
        this.requestId = requestId;
        this.action = Objects.requireNonNull(action, "action");
        this.key = requireText(key, "key");
        this.payloadFingerprint = requireText(payloadFingerprint, "payloadFingerprint");
    }

    public UUID actorAccountId() { return actorAccountId; }
    public String systemSourceId() { return systemSourceId; }
    public UUID visitId() { return visitId; }
    public UUID requestId() { return requestId; }
    public DiagnosticAction action() { return action; }
    public String key() { return key; }
    public String payloadFingerprint() { return payloadFingerprint; }

    public boolean sameOperation(DiagnosticIdempotencyIdentity other) {
        return other != null
                && Objects.equals(actorAccountId, other.actorAccountId)
                && Objects.equals(systemSourceId, other.systemSourceId)
                && visitId.equals(other.visitId)
                && Objects.equals(requestId, other.requestId)
                && action == other.action
                && key.equals(other.key);
    }

    public DiagnosticOperationOutcome replayOutcome(DiagnosticIdempotencyIdentity candidate) {
        if (!sameOperation(candidate)) return DiagnosticOperationOutcome.STATE_CONFLICT;
        return payloadFingerprint.equals(candidate.payloadFingerprint)
                ? DiagnosticOperationOutcome.ALLOWED : DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticIdempotencyIdentity that)) return false;
        return Objects.equals(actorAccountId, that.actorAccountId)
                && Objects.equals(systemSourceId, that.systemSourceId)
                && Objects.equals(visitId, that.visitId)
                && Objects.equals(requestId, that.requestId)
                && action == that.action
                && Objects.equals(key, that.key)
                && Objects.equals(payloadFingerprint, that.payloadFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actorAccountId, systemSourceId, visitId, requestId, action, key, payloadFingerprint);
    }

    @Override
    public String toString() {
        return "DiagnosticIdempotencyIdentity[actorAccountId=" + actorAccountId + ", systemSourceId="
                + systemSourceId + ", visitId=" + visitId + ", requestId=" + requestId + ", action=" + action
                + ", key=" + key + ", payloadFingerprint=" + payloadFingerprint + "]";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
