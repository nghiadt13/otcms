package fu.fa26.sep490.g58.otcms_be.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "consent_evidence", uniqueConstraints = @UniqueConstraint(name = "uk_consent_evidence_version", columnNames = {"account_id", "actor_type", "terms_version", "privacy_version"}))
@Getter @Setter @NoArgsConstructor
public class ConsentEvidence {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false) private Account account;
    @Enumerated(EnumType.STRING) @Column(name = "actor_type", nullable = false, length = 16) private ConsentActorType actorType;
    @Column(name = "terms_version", nullable = false, length = 64) private String termsVersion;
    @Column(name = "privacy_version", nullable = false, length = 64) private String privacyVersion;
    @Column(name = "accepted_at", nullable = false) private Instant acceptedAt;
}
