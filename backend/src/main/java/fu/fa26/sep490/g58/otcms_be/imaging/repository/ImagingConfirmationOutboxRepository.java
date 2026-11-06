package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingConfirmationOutboxJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingConfirmationOutboxRepository
        extends JpaRepository<ImagingConfirmationOutboxJpaEntity, UUID> {
    Optional<ImagingConfirmationOutboxJpaEntity> findByEventId(UUID eventId);
    Optional<ImagingConfirmationOutboxJpaEntity> findByOperationId(UUID operationId);
    List<ImagingConfirmationOutboxJpaEntity> findAllByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAscIdAsc(
            String status, Instant dueAt, Pageable pageable);
}
