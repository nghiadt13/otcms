package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImagingReconciliationCaseRepository
        extends JpaRepository<ImagingReconciliationCaseJpaEntity, UUID> {
    List<ImagingReconciliationCaseJpaEntity> findAllByOperationIdAndStateNot(UUID operationId, String state);
    List<ImagingReconciliationCaseJpaEntity> findAllByEventIdAndStateNot(UUID eventId, String state);
    List<ImagingReconciliationCaseJpaEntity> findAllByStateAndAssignedOwnerQueueAndIdGreaterThanOrderByIdAsc(
            String state, String assignedOwnerQueue, UUID afterId, Pageable pageable);

    @Query("""
            select item from ImagingReconciliationCaseJpaEntity item
            where item.assignedOwnerQueue = 'IMAGING_OPERATIONS'
              and (:state is null or item.state = :state)
              and (:classification is null or item.classification = :classification)
              and (:category is null or item.category = :category)
            """)
    Page<ImagingReconciliationCaseJpaEntity> findQueue(@Param("state") String state,
            @Param("classification") String classification, @Param("category") String category,
            Pageable pageable);

    @Modifying
    @Query("""
            update ImagingReconciliationCaseJpaEntity item
               set item.state = :nextState, item.resolutionRef = :resolutionRef,
                   item.resolvedAt = :resolvedAt, item.updatedAt = :updatedAt,
                   item.resolutionDecision = :decision
             where item.id = :caseId and item.state = :expectedState and item.updatedAt = :expectedUpdatedAt
            """)
    int recordAdminDecision(@Param("caseId") UUID caseId, @Param("expectedState") String expectedState,
            @Param("nextState") String nextState, @Param("decision") String decision,
            @Param("resolutionRef") String resolutionRef, @Param("resolvedAt") Instant resolvedAt,
            @Param("updatedAt") Instant updatedAt, @Param("expectedUpdatedAt") Instant expectedUpdatedAt);

    @Modifying
    @Query("""
            update ImagingReconciliationCaseJpaEntity item
               set item.classification = 'LINKED', item.state = 'RESOLVED',
                   item.resolvedAt = :resolvedAt, item.updatedAt = :resolvedAt
             where item.id = :caseId and item.state = 'OPEN'
               and item.classification = 'RETRYABLE' and item.updatedAt = :expectedUpdatedAt
            """)
    int resolveRetryableWithLinkedEvidence(@Param("caseId") UUID caseId,
            @Param("expectedUpdatedAt") Instant expectedUpdatedAt, @Param("resolvedAt") Instant resolvedAt);

    @Modifying
    @Query(value = """
            insert into imaging_reconciliation_cases
                (id, operation_id, event_id, category, classification, state,
                 safe_reason_code, safe_evidence_sha256, assigned_owner_queue)
            values (:id, :operationId, :eventId, :category, :classification, 'OPEN',
                    :safeReasonCode, :safeEvidenceSha256, :ownerQueue)
            on conflict do nothing
            """, nativeQuery = true)
    int insertOpenCaseIfAbsent(@Param("id") UUID id, @Param("operationId") UUID operationId,
            @Param("eventId") UUID eventId, @Param("category") String category,
            @Param("classification") String classification, @Param("safeReasonCode") String safeReasonCode,
            @Param("safeEvidenceSha256") String safeEvidenceSha256, @Param("ownerQueue") String ownerQueue);
}
