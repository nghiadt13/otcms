package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingIdempotencyRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIdempotencyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maps the immutable idempotency model to its FEAT-004 JPA entity. */
@Service
public class ImagingIdempotencyPersistenceService {
    private final ImagingIdempotencyRepository idempotency;

    public ImagingIdempotencyPersistenceService(ImagingIdempotencyRepository idempotency) {
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Optional<ImagingIdempotencyRecord> find(UUID actorAccountId, UUID requestId, UUID attemptId,
            ImagingOperationType operationType, String idempotencyKey) {
        return idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
                        actorAccountId, requestId, attemptId, operationType, idempotencyKey)
                .map(ImagingIdempotencyRecordJpaEntity::toModel);
    }

    @Transactional
    public ImagingIdempotencyRecord insert(ImagingIdempotencyRecord record) {
        return idempotency.saveAndFlush(new ImagingIdempotencyRecordJpaEntity(record)).toModel();
    }
}
