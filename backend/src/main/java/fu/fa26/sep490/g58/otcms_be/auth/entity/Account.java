package fu.fa26.sep490.g58.otcms_be.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "accounts") @Getter @Setter @NoArgsConstructor
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "normalized_email", nullable = false, length = 254, unique = true) private String normalizedEmail;
    @Enumerated(EnumType.STRING) @Column(name = "account_type", nullable = false, length = 16) private AccountType accountType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AccountStatus status;
    @Column(name = "password_hash", length = 255) private String passwordHash;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(name = "failed_credential_count", nullable = false) private int failedCredentialCount;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
