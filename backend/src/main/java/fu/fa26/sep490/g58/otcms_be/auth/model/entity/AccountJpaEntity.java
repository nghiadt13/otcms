package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class AccountJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "normalized_email", length = 254, unique = true) private String normalizedEmail;
    @Column(unique = true, columnDefinition = "text") private String username;
    @Column(name = "must_change_password", nullable = false) private boolean mustChangePassword;
    @Enumerated(EnumType.STRING) @Column(name = "account_type", nullable = false, length = 16) private AccountType accountType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AccountStatus status;
    @Column(name = "password_hash", length = 255) private String passwordHash;
    @Column(name = "full_name", length = 100) private String fullName;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(name = "failed_credential_count", nullable = false) private int failedCredentialCount;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
