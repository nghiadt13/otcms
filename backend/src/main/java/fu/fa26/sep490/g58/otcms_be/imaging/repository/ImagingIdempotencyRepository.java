package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingIdempotencyRepository extends JpaRepository<ImagingIdempotencyRecordJpaEntity, UUID> {
    Optional<ImagingIdempotencyRecordJpaEntity> findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
            UUID actorAccountId, UUID requestId, UUID attemptId, ImagingOperationType operationType,
            String idempotencyKey);
}
