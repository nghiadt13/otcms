package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable identity for a verified service-use attempt; failure/retake lifecycle is excluded. */
public final class DiagnosticAttempt {
    private final UUID id;
    private final UUID requestId;
    private final int attemptNumber;
    private final UUID firstUseEventId;
    private final Instant firstUseAt;
    private final String studyRef;

    public DiagnosticAttempt(UUID id, UUID requestId, int attemptNumber, UUID firstUseEventId,
            Instant firstUseAt, String studyRef) {
        this.id = Objects.requireNonNull(id, "id");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.attemptNumber = attemptNumber;
        this.firstUseEventId = Objects.requireNonNull(firstUseEventId, "firstUseEventId");
        this.firstUseAt = Objects.requireNonNull(firstUseAt, "firstUseAt");
        this.studyRef = studyRef;
        if (attemptNumber != 1) {
            throw new IllegalArgumentException("only the first attempt is supported until retake policy is approved");
        }
        if (studyRef != null && studyRef.isBlank()) throw new IllegalArgumentException("studyRef cannot be blank");
    }

    public UUID id() { return id; }
    public UUID requestId() { return requestId; }
    public int attemptNumber() { return attemptNumber; }
    public UUID firstUseEventId() { return firstUseEventId; }
    public Instant firstUseAt() { return firstUseAt; }
    public String studyRef() { return studyRef; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticAttempt that)) return false;
        return attemptNumber == that.attemptNumber
                && Objects.equals(id, that.id)
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(firstUseEventId, that.firstUseEventId)
                && Objects.equals(firstUseAt, that.firstUseAt)
                && Objects.equals(studyRef, that.studyRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, requestId, attemptNumber, firstUseEventId, firstUseAt, studyRef);
    }

    @Override
    public String toString() {
        return "DiagnosticAttempt[id=" + id + ", requestId=" + requestId + ", attemptNumber=" + attemptNumber
                + ", firstUseEventId=" + firstUseEventId + ", firstUseAt=" + firstUseAt
                + ", studyRef=" + studyRef + "]";
    }

}
