package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitImagingFindingsReleaseJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitImagingFindingsReleaseRepository
        extends JpaRepository<VisitImagingFindingsReleaseJpaEntity, UUID> {
    Optional<VisitImagingFindingsReleaseJpaEntity> findByEventId(UUID eventId);
}
