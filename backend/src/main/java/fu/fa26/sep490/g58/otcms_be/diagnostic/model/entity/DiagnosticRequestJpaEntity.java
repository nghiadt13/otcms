package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_requests")
public class DiagnosticRequestJpaEntity {
    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Authenticated Doctor account ID, not doctor_profiles.id. Kept for persisted/API compatibility. */
    @Column(name = "ordering_doctor_id", nullable = false)
    private UUID orderingDoctorId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    /** Null while PENDING awaiting separate UC24 room assignment. */
    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "imaging_type", nullable = false, length = 80)
    private String imagingType;

    @Column(name = "body_area", nullable = false, length = 255)
    private String bodyArea;

    @Column(name = "clinical_reason", nullable = false, columnDefinition = "text")
    private String clinicalReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DiagnosticRequestStatus status;

    @Column(name = "cancellation_cause", length = 64)
    private String cancellationCause;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "current_attempt_id")
    private UUID currentAttemptId;

    /** Clinical service order this request fulfils; null for requests created without an order. */
    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected DiagnosticRequestJpaEntity() { }

    public DiagnosticRequestJpaEntity(UUID id, UUID visitId, UUID patientId, UUID orderingDoctorId,
            UUID serviceId, UUID roomId, String imagingType, DiagnosticRequestStatus status,
            String cancellationCause, String cancellationReason, UUID currentAttemptId) {
        this(id, visitId, patientId, orderingDoctorId, serviceId, roomId, imagingType,
                "UNSPECIFIED", "Legacy diagnostic request", status, cancellationCause, cancellationReason,
                currentAttemptId);
    }

    public DiagnosticRequestJpaEntity(UUID id, UUID visitId, UUID patientId, UUID orderingDoctorId,
            UUID serviceId, UUID roomId, String imagingType, String bodyArea, String clinicalReason,
            DiagnosticRequestStatus status, String cancellationCause, String cancellationReason,
            UUID currentAttemptId) {
        this.id = id;
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
        this.currentAttemptId = currentAttemptId;
    }

    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientId() { return patientId; }
    public UUID getOrderingDoctorId() { return orderingDoctorId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getRoomId() { return roomId; }
    public String getImagingType() { return imagingType; }
    public String getBodyArea() { return bodyArea; }
    public String getClinicalReason() { return clinicalReason; }
    public DiagnosticRequestStatus getStatus() { return status; }
    public String getCancellationCause() { return cancellationCause; }
    public String getCancellationReason() { return cancellationReason; }
    public UUID getCurrentAttemptId() { return currentAttemptId; }
    public UUID getServiceOrderId() { return serviceOrderId; }
    public void setServiceOrderId(UUID serviceOrderId) { this.serviceOrderId = serviceOrderId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRoomId(UUID roomId) { this.roomId = roomId; }
    public void setBodyArea(String bodyArea) { this.bodyArea = bodyArea; }
    public void setClinicalReason(String clinicalReason) { this.clinicalReason = clinicalReason; }
    public void setStatus(DiagnosticRequestStatus status) { this.status = status; }
    public void setCancellationCause(String cancellationCause) { this.cancellationCause = cancellationCause; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public void setCurrentAttemptId(UUID currentAttemptId) { this.currentAttemptId = currentAttemptId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
