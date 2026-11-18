package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosticReportJpaRepository extends JpaRepository<DiagnosticReportJpaEntity, UUID> {
    /** Scalar imaging projection avoids loading unrelated diagnostic payloads. */
    interface ResultSheetReport {
        UUID getRequestId();
        UUID getVisitId();
        String getStudyRef();
        int getReportVersion();
        DiagnosticReportValidationStatus getValidationStatus();
        Instant getCompletedAt();
        Instant getReleasedAt();
        UUID getReportId();
        String getFindingsText();
    }

    @Query("""
            select d.requestId as requestId, d.visitId as visitId, d.studyRef as studyRef,
                d.reportVersion as reportVersion, d.validationStatus as validationStatus,
                d.completedAt as completedAt, d.releasedAt as releasedAt, d.reportId as reportId,
                d.findingsText as findingsText
            from DiagnosticReportJpaEntity d
            where d.requestId in :requestIds and d.state = :state
            order by d.reportVersion desc
            """)
    List<ResultSheetReport> findResultSheetReports(@Param("requestIds") Collection<UUID> requestIds,
            @Param("state") DiagnosticFindingsVersionState state);
    @org.springframework.data.jpa.repository.Query("select r from DiagnosticReportJpaEntity r where r.requestId = :orderId and r.resultType = 'LAB' order by r.reportVersion desc")
    List<DiagnosticReportJpaEntity> findLaboratoryVersions(@org.springframework.data.repository.query.Param("orderId") UUID orderId);
    List<DiagnosticReportJpaEntity> findAllByRequestIdInAndState(Collection<UUID> requestIds,
            DiagnosticFindingsVersionState state);

    Optional<DiagnosticReportJpaEntity> findByRequestIdAndReportVersion(UUID requestId, int version);

    Optional<DiagnosticReportJpaEntity> findFirstByRequestIdOrderByReportVersionDesc(UUID requestId);

    Optional<DiagnosticReportJpaEntity> findFirstByRequestIdAndStateOrderByReportVersionDesc(
            UUID requestId, DiagnosticFindingsVersionState state);

    Optional<DiagnosticReportJpaEntity> findByReportIdAndRequestId(UUID reportId, UUID requestId);

    Optional<DiagnosticReportJpaEntity> findByRequestIdAndDraftIdempotencyKey(UUID requestId, String key);
    Optional<DiagnosticReportJpaEntity> findByRequestIdAndCorrectionIdempotencyKey(UUID requestId, String key);
    Optional<DiagnosticReportJpaEntity> findByRequestIdAndUploadIdempotencyKey(UUID requestId, String key);
    Optional<DiagnosticReportJpaEntity> findByRequestIdAndCompleteIdempotencyKey(UUID requestId, String key);

    List<DiagnosticReportJpaEntity> findAllByRequestIdOrderByReportVersionDesc(UUID requestId);

    Optional<DiagnosticReportJpaEntity> findFirstByRequestIdAndValidationStatusOrderByReportVersionDesc(
            UUID requestId, DiagnosticReportValidationStatus validationStatus);

    Optional<DiagnosticReportJpaEntity> findByIdAndRequestId(UUID id, UUID requestId);

    Optional<DiagnosticReportJpaEntity> findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(
            UUID requestId);
}
