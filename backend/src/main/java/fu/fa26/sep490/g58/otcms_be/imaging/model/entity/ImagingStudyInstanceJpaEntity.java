package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "imaging_study_instances", uniqueConstraints =
        @UniqueConstraint(name = "uk_imaging_instance_uid", columnNames = {"series_id", "sop_instance_uid"}))
public class ImagingStudyInstanceJpaEntity {
    @Id
    private UUID id;
    @Column(name = "series_id", nullable = false)
    private UUID seriesId;
    @Column(name = "sop_instance_uid", nullable = false, length = 64)
    private String sopInstanceUid;
    @Column(name = "archive_instance_ref", nullable = false, unique = true, columnDefinition = "text")
    private String archiveInstanceRef;
    @Column(name = "frame_count")
    private Integer frameCount;

    protected ImagingStudyInstanceJpaEntity() { }

    public ImagingStudyInstanceJpaEntity(UUID id, UUID seriesId, String sopInstanceUid,
            String archiveInstanceRef, Integer frameCount) {
        this.id = id;
        this.seriesId = seriesId;
        this.sopInstanceUid = sopInstanceUid;
        this.archiveInstanceRef = archiveInstanceRef;
        this.frameCount = frameCount;
    }

    public UUID getId() { return id; }
    public UUID getSeriesId() { return seriesId; }
    public String getSopInstanceUid() { return sopInstanceUid; }
    public String getArchiveInstanceRef() { return archiveInstanceRef; }
    public Integer getFrameCount() { return frameCount; }
}
