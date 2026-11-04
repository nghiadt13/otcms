package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationPurpose;
import java.util.Objects;
import java.util.UUID;

/** Authenticated request metadata passed from the service boundary into domain policy. */
public final class DiagnosticRequestContext {
    private final DiagnosticActor actor;
    private final DiagnosticOperationPurpose purpose;
    private final UUID visitId;
    private final UUID requestId;
    private final String correlationId;

    public DiagnosticRequestContext(DiagnosticActor actor, DiagnosticOperationPurpose purpose,
            UUID visitId, UUID requestId, String correlationId) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.visitId = Objects.requireNonNull(visitId, "visitId");
        this.requestId = requestId;
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        this.correlationId = correlationId.trim();
    }

    public DiagnosticActor actor() { return actor; }
    public DiagnosticOperationPurpose purpose() { return purpose; }
    public UUID visitId() { return visitId; }
    public UUID requestId() { return requestId; }
    public String correlationId() { return correlationId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticRequestContext that)) return false;
        return Objects.equals(actor, that.actor) && purpose == that.purpose
                && Objects.equals(visitId, that.visitId) && Objects.equals(requestId, that.requestId)
                && Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, purpose, visitId, requestId, correlationId);
    }

    @Override
    public String toString() {
        return "DiagnosticRequestContext[actor=" + actor + ", purpose=" + purpose + ", visitId=" + visitId
                + ", requestId=" + requestId + ", correlationId=" + correlationId + "]";
    }
}
