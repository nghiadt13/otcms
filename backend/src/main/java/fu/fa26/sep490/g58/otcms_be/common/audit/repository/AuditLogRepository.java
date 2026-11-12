package fu.fa26.sep490.g58.otcms_be.common.audit.repository;

import fu.fa26.sep490.g58.otcms_be.common.audit.model.AuditLogJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Append-only persistence; deliberately exposes no update/delete methods. */
public interface AuditLogRepository extends Repository<AuditLogJpaEntity, UUID> {
    String INSERT = """
            INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type,
                entity_id, record_label, correction_reason, visible_in_activity_log, actor_account_id, actor_type,
                before_state, after_state, metadata, correlation_id, client_ip, user_agent, identifier_hmac, outcome, dedup_key)
            VALUES (:#{#log.id}, :#{#log.occurredAt}, :#{#log.source}, :#{#log.category}, :#{#log.action}, :#{#log.recordType},
                :#{#log.entityId}, :#{#log.recordLabel}, :#{#log.correctionReason}, :#{#log.visibleInActivityLog},
                :#{#log.actorAccountId}, :#{#log.actorType}, CAST(:#{#log.beforeState} AS jsonb),
                CAST(:#{#log.afterState} AS jsonb), CAST(:#{#log.metadata} AS jsonb), :#{#log.correlationId},
                :#{#log.clientIp}, :#{#log.userAgent}, :#{#log.identifierHmac}, :#{#log.outcome}, :#{#log.dedupKey})
            """;

    @Modifying
    @Query(value = INSERT, nativeQuery = true)
    int insert(@Param("log") AuditLogJpaEntity log);

    @Modifying
    @Query(value = INSERT + " ON CONFLICT (dedup_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("log") AuditLogJpaEntity log);

    long countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(
            String source, String action, String outcome, String identifierHmac, Instant since);
}
