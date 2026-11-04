package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyInstanceJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingStudyInstanceRepository extends JpaRepository<ImagingStudyInstanceJpaEntity, UUID> {
    List<ImagingStudyInstanceJpaEntity> findAllBySeriesIdOrderBySopInstanceUid(UUID seriesId);
    Optional<ImagingStudyInstanceJpaEntity> findBySeriesIdAndSopInstanceUid(UUID seriesId, String sopUid);
}
