package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** FEAT-003 service projection; contains no JPA or HTTP types. */
public final class DiagnosticRequestView {
    private final UUID requestId;
    private final UUID visitId;
    private final UUID patientId;
    private final UUID orderingDoctorId;
    private final UUID serviceId;
    private final UUID roomId;
    private final String imagingType;
    private final String bodyArea;
    private final String clinicalReason;
    private final DiagnosticRequestStatus status;
    private final String cancellationCause;
    private final String cancellationReason;
    private final String currentStudyRef;
    private final long version;
    private final Instant createdAt;

    public DiagnosticRequestView(UUID requestId, UUID visitId, UUID patientId, UUID orderingDoctorId,
            UUID serviceId, UUID roomId, String imagingType, String bodyArea, String clinicalReason,
            DiagnosticRequestStatus status, String cancellationCause, String cancellationReason,
            String currentStudyRef, long version, Instant createdAt) {
        this.requestId = requestId;
        this.visitId = visitId;
        this.patientId = patientId;
        this.orderingDoctorId = orderingDoctorId;
        this.serviceId = serviceId;
        this.roomId = roomId;
        this.imagingType = imagingType;
        this.bodyArea = bodyArea;
        this.clinicalReason = clinicalReason;
        this.status = status;
        this.cancellationCause = cancellationCause;
        this.cancellationReason = cancellationReason;
        this.currentStudyRef = currentStudyRef;
        this.version = version;
        this.createdAt = createdAt;
    }

    public UUID requestId() { return requestId; }
    public UUID visitId() { return visitId; }
    public UUID patientId() { return patientId; }
    public UUID orderingDoctorId() { return orderingDoctorId; }
    public UUID serviceId() { return serviceId; }
    public UUID roomId() { return roomId; }
    public String imagingType() { return imagingType; }
    public String bodyArea() { return bodyArea; }
    public String clinicalReason() { return clinicalReason; }
    public DiagnosticRequestStatus status() { return status; }
    public String cancellationCause() { return cancellationCause; }
    public String cancellationReason() { return cancellationReason; }
    public String currentStudyRef() { return currentStudyRef; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticRequestView that)) return false;
        return version == that.version
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(visitId, that.visitId)
                && Objects.equals(patientId, that.patientId)
                && Objects.equals(orderingDoctorId, that.orderingDoctorId)
                && Objects.equals(serviceId, that.serviceId)
                && Objects.equals(roomId, that.roomId)
                && Objects.equals(imagingType, that.imagingType)
                && Objects.equals(bodyArea, that.bodyArea)
                && Objects.equals(clinicalReason, that.clinicalReason)
                && status == that.status
                && Objects.equals(cancellationCause, that.cancellationCause)
                && Objects.equals(cancellationReason, that.cancellationReason)
                && Objects.equals(currentStudyRef, that.currentStudyRef)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, visitId, patientId, orderingDoctorId, serviceId, roomId,
                imagingType, bodyArea, clinicalReason, status, cancellationCause, cancellationReason,
                currentStudyRef, version, createdAt);
    }
}
