package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "imaging_study_series", uniqueConstraints =
        @UniqueConstraint(name = "uk_imaging_series_uid", columnNames = {"study_ref", "series_instance_uid"}))
public class ImagingStudySeriesJpaEntity {
    @Id
    private UUID id;
    @Column(name = "study_ref", nullable = false, length = 128)
    private String studyRef;
    @Column(name = "series_instance_uid", nullable = false, length = 64)
    private String seriesInstanceUid;
    @Column(name = "archive_series_ref", nullable = false, unique = true, columnDefinition = "text")
    private String archiveSeriesRef;
    @Column(name = "instance_count", nullable = false)
    private int instanceCount;

    protected ImagingStudySeriesJpaEntity() { }

    public ImagingStudySeriesJpaEntity(UUID id, String studyRef, String seriesInstanceUid,
            String archiveSeriesRef, int instanceCount) {
        this.id = id;
        this.studyRef = studyRef;
        this.seriesInstanceUid = seriesInstanceUid;
        this.archiveSeriesRef = archiveSeriesRef;
        this.instanceCount = instanceCount;
    }

    public UUID getId() { return id; }
    public String getStudyRef() { return studyRef; }
    public String getSeriesInstanceUid() { return seriesInstanceUid; }
    public String getArchiveSeriesRef() { return archiveSeriesRef; }
    public int getInstanceCount() { return instanceCount; }
}
