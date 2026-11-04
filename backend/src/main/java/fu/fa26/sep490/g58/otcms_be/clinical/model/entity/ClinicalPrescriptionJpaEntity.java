package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Owner CAS repository is the only command writer; issued aggregates have no content mutators. */
@Entity
@Table(name = "clinical_prescriptions", uniqueConstraints = @UniqueConstraint(name = "uk_clinical_prescription_visit", columnNames = "visit_id"))
public class ClinicalPrescriptionJpaEntity {
    @Id private UUID id;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "patient_profile_id", nullable = false) private UUID patientProfileId;
    @Column(name = "doctor_profile_id", nullable = false) private UUID doctorProfileId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ClinicalPrescriptionStatus status;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    @Column(name = "issuance_key", length = 128) private String issuanceKey;
    @Column(name = "issuance_expected_version") private Long issuanceExpectedVersion;

    protected ClinicalPrescriptionJpaEntity() { }
    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public ClinicalPrescriptionStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public long getVersion() { return version; }
    public String getIssuanceKey() { return issuanceKey; }
    public Long getIssuanceExpectedVersion() { return issuanceExpectedVersion; }
}
