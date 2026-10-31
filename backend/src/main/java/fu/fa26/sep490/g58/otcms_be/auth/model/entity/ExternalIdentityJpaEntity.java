package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.IdentityProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "account_identities", uniqueConstraints = @UniqueConstraint(name = "uk_external_identities_provider_subject", columnNames = {"provider", "provider_subject"}))
@Getter
@Setter
@NoArgsConstructor
public class ExternalIdentityJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false) private AccountJpaEntity account;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private IdentityProvider provider;
    @Column(name = "provider_subject", nullable = false, length = 255) private String providerSubject;
    @Column(name = "normalized_email_at_link", nullable = false, length = 254) private String normalizedEmailAtLink;
    @Column(name = "linked_at", nullable = false) private Instant linkedAt;
}
