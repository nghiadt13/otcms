package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;

public interface ImagingStudyRepository extends JpaRepository<ImagingStudyJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select study from ImagingStudyJpaEntity study where study.id = :id")
    Optional<ImagingStudyJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<ImagingStudyJpaEntity> findByStudyRef(String studyRef);

    Optional<ImagingStudyJpaEntity> findByStudyInstanceUid(String studyInstanceUid);
    List<ImagingStudyJpaEntity> findAllByRequestIdAndAttemptIdOrderByCreatedAtDesc(
            UUID requestId, UUID attemptId);

    Optional<ImagingStudyJpaEntity> findByActorAccountIdAndRequestIdAndAttemptIdAndUploadIdempotencyKey(
            UUID actorId, UUID requestId, UUID attemptId, String key);

    Optional<ImagingStudyJpaEntity> findByConfirmationEventId(UUID eventId);
    Optional<ImagingStudyJpaEntity> findByReleaseEventId(UUID eventId);

    @Query(value = """
            select * from imaging_studies
             where state = 'CONFIRMATION_PENDING' and next_confirmation_attempt_at <= :now
             order by next_confirmation_attempt_at, id limit :limit
            """, nativeQuery = true)
    List<ImagingStudyJpaEntity> findDueConfirmations(Instant now, int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImagingStudyJpaEntity study
               set study.state = :nextState, study.safeOutcomeCode = :safeOutcomeCode,
                   study.version = study.version + 1, study.updatedAt = :updatedAt
             where study.id = :studyId and study.version = :expectedVersion and study.state = :expectedState
            """)
    int transitionStateIfVersionMatches(@Param("studyId") UUID studyId,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedState") ImagingOperationState expectedState,
            @Param("nextState") ImagingOperationState nextState,
            @Param("safeOutcomeCode") String safeOutcomeCode, @Param("updatedAt") Instant updatedAt);

    @Query(value = """
            select exists (select 1 from imaging_studies s
             where s.id = :id and s.dicom_index @> cast(:probe as jsonb))
            """, nativeQuery = true)
    boolean containsDicomIndex(@Param("id") UUID id, @Param("probe") String probeJson);
}
