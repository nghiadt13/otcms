package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visits")
public class ClinicVisitJpaEntity {
    @Id
    private UUID id;

    @Column(name = "patient_profile_id", nullable = false)
    private UUID patientProfileId;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "originating_appointment_id")
    private UUID originatingAppointmentId;

    @Column(name = "prior_appointment_id")
    private UUID priorAppointmentId;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "created_by_account_id", nullable = false)
    private UUID createdByAccountId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** Walk-in create replay key (was walk_in_requests); UNIQUE with created_by_account_id. WALK_IN only. */
    @Column(name = "walk_in_idempotency_key", length = 128, updatable = false)
    private String walkInIdempotencyKey;

    @Column(name = "walk_in_request_fingerprint", length = 64, updatable = false)
    private String walkInRequestFingerprint;

    protected ClinicVisitJpaEntity() { }

    public static ClinicVisitJpaEntity walkIn(UUID id, UUID patientProfileId, UUID priorAppointmentId,
            UUID receptionistAccountId, Instant arrivedAt, String idempotencyKey, String requestFingerprint) {
        ClinicVisitJpaEntity entity = new ClinicVisitJpaEntity();
        entity.id = id;
        entity.patientProfileId = patientProfileId;
        entity.source = "WALK_IN";
        entity.priorAppointmentId = priorAppointmentId;
        entity.arrivedAt = arrivedAt;
        entity.checkedInAt = arrivedAt;
        entity.createdByAccountId = receptionistAccountId;
        entity.status = "OPEN";
        entity.walkInIdempotencyKey = idempotencyKey;
        entity.walkInRequestFingerprint = requestFingerprint;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public String getSource() { return source; }
    public UUID getPriorAppointmentId() { return priorAppointmentId; }
    public Instant getArrivedAt() { return arrivedAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public String getStatus() { return status; }
    public String getWalkInIdempotencyKey() { return walkInIdempotencyKey; }
    public String getWalkInRequestFingerprint() { return walkInRequestFingerprint; }
}
