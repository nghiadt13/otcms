package fu.fa26.sep490.g58.otcms_be.idempotency.repository;

import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.entity.IdempotencyRecordJpaEntity;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordJpaEntity, UUID> {
    Optional<IdempotencyRecordJpaEntity> findByScopeAndOwnerIdAndIdempotencyKey(
            IdempotencyScope scope, UUID ownerId, String idempotencyKey);

    List<IdempotencyRecordJpaEntity> findByOwnerIdInAndScopeInOrderByCreatedAtDesc(
            Collection<UUID> ownerIds, Collection<IdempotencyScope> scopes);

    /** Insert without raising a unique violation, so the current transaction remains usable for replay lookup. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into idempotency_records
                (id, scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
            values (:id, :scope, :ownerId, :key, :fingerprint, :resourceId, :createdAt)
            on conflict (scope, owner_id, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("scope") String scope,
            @Param("ownerId") UUID ownerId, @Param("key") String key,
            @Param("fingerprint") String fingerprint, @Param("resourceId") UUID resourceId,
            @Param("createdAt") Timestamp createdAt);
}
