package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Metadata only. Payload reads/writes are isolated from listing and authorization. */
@Entity @Table(name = "clinical_medical_documents")
public class ClinicalMedicalDocumentJpaEntity {
    @Id private UUID id;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "patient_profile_id", nullable = false) private UUID patientProfileId;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 32) private ClinicalMedicalDocumentType documentType;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "media_type", nullable = false, length = 80) private String mediaType;
    @Column(name = "byte_size", nullable = false) private long byteSize;
    @Column(name = "released_at") private Instant releasedAt;
    @Column(name = "released_by_doctor_profile_id") private UUID releasedByDoctorProfileId;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected ClinicalMedicalDocumentJpaEntity() { }
    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public ClinicalMedicalDocumentType getDocumentType() { return documentType; }
    public String getTitle() { return title; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public long getByteSize() { return byteSize; }
    public Instant getReleasedAt() { return releasedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
