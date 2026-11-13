package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImagingStudyRepository extends JpaRepository<ImagingStudyJpaEntity, UUID> {
    Optional<ImagingStudyJpaEntity> findByStudyRef(String studyRef);

    // The D2 adapter still addresses studies by their public reference until its separate rewrite.
    default Optional<ImagingStudyJpaEntity> findById(String studyRef) { return findByStudyRef(studyRef); }

    @Query("select study from ImagingStudyJpaEntity study where study.id = :ingestOperationId")
    Optional<ImagingStudyJpaEntity> findByIngestOperationId(UUID ingestOperationId);
    Optional<ImagingStudyJpaEntity> findByStudyInstanceUid(String studyInstanceUid);
    List<ImagingStudyJpaEntity> findAllByRequestIdAndAttemptIdOrderByCreatedAtDesc(
            UUID requestId, UUID attemptId);

    @Query("""
            select study from ImagingStudyJpaEntity study
             where study.actorAccountId = :actorId and study.requestId = :requestId
               and study.attemptId = :attemptId and study.uploadIdempotencyKey = :key
            """)
    Optional<ImagingStudyJpaEntity> findByUploadScope(UUID actorId, UUID requestId, UUID attemptId, String key);

    Optional<ImagingStudyJpaEntity> findByConfirmationEventId(UUID eventId);
    Optional<ImagingStudyJpaEntity> findByReleaseEventId(UUID eventId);

    @Query(value = """
            select * from imaging_studies
             where state = 'CONFIRMATION_PENDING' and next_confirmation_attempt_at <= :now
             order by next_confirmation_attempt_at, id limit :limit
            """, nativeQuery = true)
    List<ImagingStudyJpaEntity> findDueConfirmations(Instant now, int limit);

    @Query(value = """
            select exists (select 1 from imaging_studies s
             where s.id = :id and s.dicom_index @> cast(:probe as jsonb))
            """, nativeQuery = true)
    boolean containsDicomIndex(@Param("id") UUID id, @Param("probe") String probeJson);
}
