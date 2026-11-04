package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReleaseInboxJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingReleaseInboxRepository extends JpaRepository<ImagingReleaseInboxJpaEntity, UUID> {
    Optional<ImagingReleaseInboxJpaEntity> findByEventId(UUID eventId);
}
