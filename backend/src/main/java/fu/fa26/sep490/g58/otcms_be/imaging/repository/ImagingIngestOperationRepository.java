package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImagingIngestOperationRepository
        extends JpaRepository<ImagingIngestOperationJpaEntity, UUID> {
    Optional<ImagingIngestOperationJpaEntity> findFirstByRequestIdAndAttemptIdOrderByCreatedAtDescIdDesc(
            UUID requestId, UUID attemptId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImagingIngestOperationJpaEntity operation
               set operation.state = :nextState,
                   operation.safeOutcomeCode = :safeOutcomeCode,
                   operation.version = operation.version + 1,
                   operation.updatedAt = :updatedAt
             where operation.id = :operationId
               and operation.version = :expectedVersion
               and operation.state = :expectedState
            """)
    int transitionStateIfVersionMatches(@Param("operationId") UUID operationId,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedState") ImagingOperationState expectedState,
            @Param("nextState") ImagingOperationState nextState,
            @Param("safeOutcomeCode") String safeOutcomeCode,
            @Param("updatedAt") Instant updatedAt);
}
