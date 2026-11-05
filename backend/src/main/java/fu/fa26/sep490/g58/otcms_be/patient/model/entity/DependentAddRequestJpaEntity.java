package fu.fa26.sep490.g58.otcms_be.patient.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A dependent-add request waiting for the requester's email OTP (UC13). */
@Entity
@Table(name = "dependent_add_requests")
@Getter @Setter @NoArgsConstructor
public class DependentAddRequestJpaEntity {
    @Id private UUID id;
    @Column(name = "requester_account_id", nullable = false) private UUID requesterAccountId;
    @Column(name = "full_name", nullable = false, length = 100) private String fullName;
    @Column(name = "date_of_birth", nullable = false) private LocalDate dateOfBirth;
    @Column(name = "gender", nullable = false, length = 16) private String gender;
    @Column(name = "national_id", length = 20) private String nationalId;
    @Column(name = "identity_document_type", length = 16) private String identityDocumentType;
    @Column(name = "relationship", nullable = false, length = 16) private String relationship;
    @Column(name = "otp_hash", nullable = false, length = 255) private String otpHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
