package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable FEAT-003-owned request state. Persistence and version locking are T011. */
public final class DiagnosticRequest {
    private final UUID id;
    private final UUID visitId;
    private final UUID patientId;
    private final UUID orderingDoctorId;
    private final UUID serviceId;
    private final String imagingType;
    private final DiagnosticRequestStatus status;
    private final DiagnosticAttempt currentAttempt;
    private final long version;
    private final Instant createdAt;

    public DiagnosticRequest(UUID id, UUID visitId, UUID patientId, UUID orderingDoctorId,
            UUID serviceId, String imagingType, DiagnosticRequestStatus status,
            DiagnosticAttempt currentAttempt, long version, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.visitId = Objects.requireNonNull(visitId, "visitId");
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.orderingDoctorId = Objects.requireNonNull(orderingDoctorId, "orderingDoctorId");
        this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
        this.imagingType = requireText(imagingType, "imagingType");
        this.status = Objects.requireNonNull(status, "status");
        this.currentAttempt = currentAttempt;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if (currentAttempt != null && !currentAttempt.requestId().equals(id)) {
            throw new IllegalArgumentException("current attempt must belong to this request");
        }
        if ((status == DiagnosticRequestStatus.PENDING || status == DiagnosticRequestStatus.CANCELLED)
                && currentAttempt != null) {
            throw new IllegalArgumentException("pending or cancelled request cannot have a current attempt");
        }
        if ((status == DiagnosticRequestStatus.IN_PROGRESS
                || status == DiagnosticRequestStatus.RESULT_AVAILABLE
                || status == DiagnosticRequestStatus.REVIEWED) && currentAttempt == null) {
            throw new IllegalArgumentException("started request must have a current attempt");
        }
        if (status == DiagnosticRequestStatus.IN_PROGRESS && currentAttempt != null
                && currentAttempt.studyRef() != null) {
            throw new IllegalArgumentException("in-progress request cannot already link a study");
        }
        if ((status == DiagnosticRequestStatus.RESULT_AVAILABLE || status == DiagnosticRequestStatus.REVIEWED)
                && (currentAttempt == null || currentAttempt.studyRef() == null)) {
            throw new IllegalArgumentException("available result requires a confirmed current study");
        }
    }

    public UUID id() { return id; }
    public UUID visitId() { return visitId; }
    public UUID patientId() { return patientId; }
    public UUID orderingDoctorId() { return orderingDoctorId; }
    public UUID serviceId() { return serviceId; }
    public String imagingType() { return imagingType; }
    public DiagnosticRequestStatus status() { return status; }
    public DiagnosticAttempt currentAttempt() { return currentAttempt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }

    public static DiagnosticRequest create(UUID id, UUID visitId, UUID patientId, UUID doctorId,
            UUID serviceId, String imagingType, Instant createdAt) {
        return new DiagnosticRequest(id, visitId, patientId, doctorId, serviceId, imagingType,
                DiagnosticRequestStatus.PENDING, null, 0, createdAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticRequest that)) return false;
        return version == that.version
                && Objects.equals(id, that.id)
                && Objects.equals(visitId, that.visitId)
                && Objects.equals(patientId, that.patientId)
                && Objects.equals(orderingDoctorId, that.orderingDoctorId)
                && Objects.equals(serviceId, that.serviceId)
                && Objects.equals(imagingType, that.imagingType)
                && status == that.status
                && Objects.equals(currentAttempt, that.currentAttempt)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, visitId, patientId, orderingDoctorId, serviceId, imagingType,
                status, currentAttempt, version, createdAt);
    }

    @Override
    public String toString() {
        return "DiagnosticRequest[id=" + id + ", visitId=" + visitId + ", patientId=" + patientId
                + ", orderingDoctorId=" + orderingDoctorId + ", serviceId=" + serviceId
                + ", imagingType=" + imagingType + ", status=" + status + ", currentAttempt=" + currentAttempt
                + ", version=" + version + ", createdAt=" + createdAt + "]";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
