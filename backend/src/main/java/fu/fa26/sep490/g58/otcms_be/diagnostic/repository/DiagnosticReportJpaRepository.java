package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticReportJpaRepository extends JpaRepository<DiagnosticReportJpaEntity, UUID> {
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
