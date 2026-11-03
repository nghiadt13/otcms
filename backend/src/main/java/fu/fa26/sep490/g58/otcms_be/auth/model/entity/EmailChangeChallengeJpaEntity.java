package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_change_challenges")
@Getter @Setter @NoArgsConstructor
public class EmailChangeChallengeJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "normalized_target_email", nullable = false, length = 254) private String normalizedTargetEmail;
    @Column(name = "otp_hash", nullable = false, length = 255) private String otpHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
