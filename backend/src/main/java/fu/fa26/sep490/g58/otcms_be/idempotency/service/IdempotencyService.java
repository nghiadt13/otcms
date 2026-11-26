package fu.fa26.sep490.g58.otcms_be.idempotency.service;

import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.dto.IdempotencyReplay;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application service for recording and resolving idempotent operation keys. */
public interface IdempotencyService {
    Optional<IdempotencyReplay> find(IdempotencyScope scope, UUID ownerId, String key);

    /** Finds the newest replay per owner and scope, used to enrich batches of clinical execution rows. */
    List<IdempotencyReplay> findLatest(Collection<UUID> ownerIds, Collection<IdempotencyScope> scopes);

    boolean record(IdempotencyScope scope, UUID ownerId, String key, String fingerprint, UUID resourceId);

    boolean record(IdempotencyScope scope, UUID ownerId, String key, String fingerprint,
            UUID resourceId, Instant createdAt);
}
