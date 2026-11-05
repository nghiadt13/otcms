package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingStudyRepository extends JpaRepository<ImagingStudyJpaEntity, String> {
    Optional<ImagingStudyJpaEntity> findByIngestOperationId(UUID ingestOperationId);
    Optional<ImagingStudyJpaEntity> findByStudyInstanceUid(String studyInstanceUid);
    List<ImagingStudyJpaEntity> findAllByRequestIdAndAttemptIdOrderByCreatedAtDesc(
            UUID requestId, UUID attemptId);
}
