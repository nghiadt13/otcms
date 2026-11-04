package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "imaging_uid_claims")
public class ImagingUidClaimJpaEntity {
    @EmbeddedId
    private ImagingUidClaimId id;
    @Column(name = "study_ref", nullable = false, length = 128)
    private String studyRef;
    @Column(name = "claimed_at", nullable = false, insertable = false, updatable = false)
    private Instant claimedAt;

    protected ImagingUidClaimJpaEntity() { }

    public ImagingUidClaimJpaEntity(ImagingUidClaimId id, String studyRef) {
        this.id = id;
        this.studyRef = studyRef;
    }

    public ImagingUidClaimId getId() { return id; }
    public String getStudyRef() { return studyRef; }
    public Instant getClaimedAt() { return claimedAt; }
}
