package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.ConsentActorType;
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
    @Enumerated(EnumType.STRING) @Column(name = "account_type", nullable = false, length = 16) private AccountType accountType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AccountStatus status;
    @Column(name = "password_hash", length = 255) private String passwordHash;
    @Column(name = "display_name", length = 100) private String displayName;
    @Column(name = "full_name", length = 100) private String fullName;
    @Column(unique = true, columnDefinition = "text") private String username;
    @Column(name = "must_change_password", nullable = false) private boolean mustChangePassword;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(name = "failed_credential_count", nullable = false) private int failedCredentialCount;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    // Avatar (final-db V110, merged from the old avatar table). avatar_content (bytea) is not mapped: read and write it
    // through the native AccountRepository avatar methods only.
    @Column(name = "avatar_content_type", length = 32, insertable = false, updatable = false) private String avatarContentType;
    @Column(name = "avatar_updated_at", insertable = false, updatable = false) private Instant avatarUpdatedAt;
    // Newest consent acceptance (final-db V110, merged from the old consent table); history is in audit_logs.
    @Column(name = "terms_version", length = 64) private String termsVersion;
    @Column(name = "privacy_version", length = 64) private String privacyVersion;
    @Enumerated(EnumType.STRING) @Column(name = "consent_actor_type", length = 16) private ConsentActorType consentActorType;
    @Column(name = "consent_accepted_at") private Instant consentAcceptedAt;
}
