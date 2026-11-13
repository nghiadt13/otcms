package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.Gender;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.PhoneOwnerType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.IdentityDocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "patient_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PatientProfileJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "account_id", unique = true) private UUID accountId;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id", insertable = false, updatable = false) private AccountJpaEntity account;
    @Column(name = "full_name", nullable = false, length = 100) private String fullName;
    @Column(name = "phone_canonical", length = 16) private String phoneCanonical;
    @Enumerated(EnumType.STRING) @Column(name = "phone_owner_type", length = 16) private PhoneOwnerType phoneOwnerType;
    @Column(name = "date_of_birth", nullable = false) private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Gender gender;
    @Column(columnDefinition = "text") private String address;
    @Column(name = "national_id", length = 20) private String nationalId;
    @Enumerated(EnumType.STRING)
    @Column(name = "identity_document_type", length = 16) private IdentityDocumentType identityDocumentType;
    @Column(nullable = false, length = 2000, insertable = false, updatable = false) private String conditions;
    @Column(nullable = false, length = 2000, insertable = false, updatable = false) private String allergies;
    @Column(nullable = false, length = 2000, insertable = false, updatable = false) private String procedures;
    @Column(nullable = false, length = 2000, insertable = false, updatable = false) private String injuries;
    @Column(name = "orthopedic_history", nullable = false, length = 2000, insertable = false, updatable = false) private String orthopedicHistory;
    @Column(name = "medical_history_updated_at", insertable = false, updatable = false) private Instant medicalHistoryUpdatedAt;
    @Column(name = "medical_history_updated_by", insertable = false, updatable = false) private UUID medicalHistoryUpdatedBy;
    @Column(name = "medical_history_version", nullable = false, insertable = false, updatable = false) private long medicalHistoryVersion;
    @Version @Column(nullable = false) private long version;
}
