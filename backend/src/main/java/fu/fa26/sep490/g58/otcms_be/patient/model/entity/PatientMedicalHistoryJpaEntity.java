package fu.fa26.sep490.g58.otcms_be.patient.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Read projection; all writes use explicit conditional repository statements. */
@Entity
@Table(name = "patient_medical_histories")
@Getter @NoArgsConstructor
public class PatientMedicalHistoryJpaEntity {
    @Id @Column(name = "patient_profile_id") private UUID patientProfileId;
    @Column(nullable = false, length = 2000) private String conditions;
    @Column(nullable = false, length = 2000) private String allergies;
    @Column(nullable = false, length = 2000) private String procedures;
    @Column(nullable = false, length = 2000) private String injuries;
    @Column(name = "orthopedic_history", nullable = false, length = 2000) private String orthopedicHistory;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by_account_id", nullable = false) private UUID updatedByAccountId;
    @Column(nullable = false) private long version;
}
