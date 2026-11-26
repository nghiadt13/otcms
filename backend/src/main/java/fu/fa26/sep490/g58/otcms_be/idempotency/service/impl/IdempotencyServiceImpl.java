package fu.fa26.sep490.g58.otcms_be.idempotency.service.impl;

import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.dto.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.entity.IdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.idempotency.repository.IdempotencyRecordRepository;
import fu.fa26.sep490.g58.otcms_be.idempotency.service.IdempotencyService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {
    private final IdempotencyRecordRepository repository;

    public IdempotencyServiceImpl(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyReplay> find(IdempotencyScope scope, UUID ownerId, String key) {
        return repository.findByScopeAndOwnerIdAndIdempotencyKey(scope, ownerId, key)
                .map(this::toReplay);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdempotencyReplay> findLatest(Collection<UUID> ownerIds, Collection<IdempotencyScope> scopes) {
        if (ownerIds == null || ownerIds.isEmpty() || scopes == null || scopes.isEmpty()) return List.of();
        List<IdempotencyRecordJpaEntity> records =
                repository.findByOwnerIdInAndScopeInOrderByCreatedAtDesc(ownerIds, scopes);
        Set<OwnerScope> seen = new HashSet<>();
        List<IdempotencyReplay> latest = new ArrayList<>();
        for (IdempotencyRecordJpaEntity record : records) {
            if (seen.add(new OwnerScope(record.getOwnerId(), record.getScope()))) {
                latest.add(toReplay(record));
            }
        }
        return List.copyOf(latest);
    }

    @Override
    @Transactional
    public boolean record(IdempotencyScope scope, UUID ownerId, String key, String fingerprint, UUID resourceId) {
        return record(scope, ownerId, key, fingerprint, resourceId, Instant.now());
    }

    @Override
    @Transactional
    public boolean record(IdempotencyScope scope, UUID ownerId, String key, String fingerprint,
            UUID resourceId, Instant createdAt) {
        return repository.insertIfAbsent(UUID.randomUUID(), scope.name(), ownerId, key, fingerprint, resourceId,
                Timestamp.from(createdAt)) == 1;
    }

    private record OwnerScope(UUID ownerId, IdempotencyScope scope) { }

    private IdempotencyReplay toReplay(IdempotencyRecordJpaEntity record) {
        return new IdempotencyReplay(record.getScope(), record.getOwnerId(), record.getIdempotencyKey(),
                record.getRequestFingerprint(), record.getResourceId(), record.getCreatedAt());
    }
}
