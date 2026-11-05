package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImagingReconciliationCaseRepository
        extends JpaRepository<ImagingReconciliationCaseJpaEntity, UUID> {
    List<ImagingReconciliationCaseJpaEntity> findAllByOperationIdAndStateNot(UUID operationId, String state);
    List<ImagingReconciliationCaseJpaEntity> findAllByEventIdAndStateNot(UUID eventId, String state);

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
