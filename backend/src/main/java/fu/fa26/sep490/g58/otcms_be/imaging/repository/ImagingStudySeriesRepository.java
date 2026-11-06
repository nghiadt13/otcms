package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudySeriesJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingStudySeriesRepository extends JpaRepository<ImagingStudySeriesJpaEntity, UUID> {
    List<ImagingStudySeriesJpaEntity> findAllByStudyRefOrderBySeriesInstanceUid(String studyRef);
    Optional<ImagingStudySeriesJpaEntity> findByStudyRefAndSeriesInstanceUid(String studyRef, String seriesUid);
}
