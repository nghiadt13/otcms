package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticIdempotencyRecordJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosticIdempotencyRecordJpaRepository
        extends JpaRepository<DiagnosticIdempotencyRecordJpaEntity, UUID> {
    @Modifying
    @Query(value = """
            insert into diagnostic_idempotency_records (
                id, actor_account_id, visit_id, request_id, operation, idempotency_key,
                payload_fingerprint, result_outcome, result_payload
            ) values (
                :id, :actorId, :visitId, :requestId, :operation, :key,
                :fingerprint, 'ALLOWED', '{}'::jsonb
            )
            on conflict (actor_account_id, visit_id,
                (coalesce(request_id, '00000000-0000-0000-0000-000000000000'::uuid)), operation, idempotency_key)
                where actor_account_id is not null
            do nothing
            """, nativeQuery = true)
    int claimActorOperation(@Param("id") UUID id, @Param("actorId") UUID actorId,
            @Param("visitId") UUID visitId, @Param("requestId") UUID requestId,
            @Param("operation") String operation, @Param("key") String key,
            @Param("fingerprint") String fingerprint);

    @Modifying
    @Query("update DiagnosticIdempotencyRecordJpaEntity record "
            + "set record.resultPayload = :payload where record.id = :id")
    int completeResult(@Param("id") UUID id, @Param("payload") java.util.Map<String, Object> payload);

    @Query("""
            select record from DiagnosticIdempotencyRecordJpaEntity record
            where record.actorAccountId = :actorId
              and record.visitId = :visitId
              and ((:requestId is null and record.requestId is null) or record.requestId = :requestId)
              and record.operation = :operation
              and record.idempotencyKey = :key
            """)
    Optional<DiagnosticIdempotencyRecordJpaEntity> findActorOperation(
            @Param("actorId") UUID actorId,
            @Param("visitId") UUID visitId,
            @Param("requestId") UUID requestId,
            @Param("operation") String operation,
            @Param("key") String key);

    @Query("""
            select record from DiagnosticIdempotencyRecordJpaEntity record
            where record.systemSourceId = :sourceId
              and record.visitId = :visitId
              and ((:requestId is null and record.requestId is null) or record.requestId = :requestId)
              and record.operation = :operation
              and record.idempotencyKey = :key
            """)
    Optional<DiagnosticIdempotencyRecordJpaEntity> findSystemOperation(
            @Param("sourceId") String sourceId,
            @Param("visitId") UUID visitId,
            @Param("requestId") UUID requestId,
            @Param("operation") String operation,
            @Param("key") String key);
}
