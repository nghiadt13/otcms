package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "password_reset_challenges")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetChallengeJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false) private AccountJpaEntity account;
    @Column(name = "token_hash", nullable = false, length = 255, unique = true) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
