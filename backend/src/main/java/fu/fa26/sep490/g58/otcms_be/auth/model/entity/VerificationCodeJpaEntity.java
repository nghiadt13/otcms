package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose;
import jakarta.persistence.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.DependentAddPayload;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * One OTP or token challenge (final-db V110). Replaces the old email-verification, email-change and
 * password-reset tables; phase 06 also stores CARE_LINK and DEPENDENT_ADD challenges.
 * code_hash holds only the hash of the OTP or reset token, never the raw value.
 */
@Entity @Table(name = "verification_codes")
@Getter
@Setter
@NoArgsConstructor
public class VerificationCodeJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private VerificationPurpose purpose;
    @Column(name = "code_hash", nullable = false, length = 255) private String codeHash;
    /** EMAIL_CHANGE only: normalized new email. */
    @Column(name = "target_email", length = 254) private String targetEmail;
    @Column(name = "link_id") private UUID linkId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private DependentAddPayload payload;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
