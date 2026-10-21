package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "auth_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AuthSessionJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false) private AccountJpaEntity account;
    @Column(name = "token_family_id", nullable = false) private UUID tokenFamilyId;
    @Column(name = "refresh_token_hash", nullable = false, length = 255, unique = true) private String refreshTokenHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "last_rotated_at") private Instant lastRotatedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoke_reason", length = 64) private String revokeReason;
    @Column(name = "client_ip", length = 64) private String clientIp;
    @Column(name = "user_agent", length = 512) private String userAgent;
}
