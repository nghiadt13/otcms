package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "imaging_viewer_grants")
public class ImagingViewerGrantJpaEntity {
    @Id private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_sha256", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String tokenSha256;
    @Column(name = "actor_account_id", nullable = false) private UUID actorAccountId;
    @Column(nullable = false, length = 24) private String purpose;
    @Column(name = "study_ref", nullable = false, length = 128) private String studyRef;
    @Column(name = "authorized_version_ref", nullable = false, length = 128) private String authorizedVersionRef;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    protected ImagingViewerGrantJpaEntity() { }

    public ImagingViewerGrantJpaEntity(UUID id, String tokenSha256, UUID actorAccountId, String purpose,
            String studyRef, String authorizedVersionRef, Instant createdAt, Instant expiresAt, Instant revokedAt) {
        this.id = id; this.tokenSha256 = tokenSha256; this.actorAccountId = actorAccountId;
        this.purpose = purpose; this.studyRef = studyRef; this.authorizedVersionRef = authorizedVersionRef;
        this.createdAt = createdAt; this.expiresAt = expiresAt; this.revokedAt = revokedAt;
    }

    public UUID getId() { return id; }
    public String getTokenSha256() { return tokenSha256; }
    public UUID getActorAccountId() { return actorAccountId; }
    public String getPurpose() { return purpose; }
    public String getStudyRef() { return studyRef; }
    public String getAuthorizedVersionRef() { return authorizedVersionRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
