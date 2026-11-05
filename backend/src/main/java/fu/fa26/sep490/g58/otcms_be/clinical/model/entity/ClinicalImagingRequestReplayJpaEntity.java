package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinical_imaging_request_replays", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clinical_imaging_replay_doctor_key", columnNames = {"doctor_profile_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_clinical_imaging_replay_round", columnNames = "ordering_round_id")})
public class ClinicalImagingRequestReplayJpaEntity {
    @Id private UUID id;
    @Column(name = "doctor_profile_id", nullable = false) private UUID doctorProfileId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "ordering_round_id", nullable = false) private UUID orderingRoundId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ClinicalImagingRequestReplayJpaEntity() { }

    public ClinicalImagingRequestReplayJpaEntity(UUID doctorProfileId, String key, String fingerprint,
            UUID roundId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.doctorProfileId = doctorProfileId;
        this.idempotencyKey = key;
        this.requestFingerprint = fingerprint;
        this.orderingRoundId = roundId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public UUID getOrderingRoundId() { return orderingRoundId; }
    public Instant getCreatedAt() { return createdAt; }
}
