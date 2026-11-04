package fu.fa26.sep490.g58.otcms_be.patient.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "care_link_otp_challenges")
@Getter @Setter @NoArgsConstructor
public class CareLinkOtpChallengeJpaEntity {
    @Id private UUID id;
    @Column(name = "link_id", nullable = false) private UUID linkId;
    @Column(name = "recipient_account_id", nullable = false) private UUID recipientAccountId;
    @Column(name = "otp_hash", nullable = false, length = 255) private String otpHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
