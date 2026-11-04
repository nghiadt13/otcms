package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticReportJpaRepository extends JpaRepository<DiagnosticReportJpaEntity, UUID> {
    List<DiagnosticReportJpaEntity> findAllByRequestIdOrderByReportVersionDesc(UUID requestId);

    Optional<DiagnosticReportJpaEntity> findFirstByRequestIdAndValidationStatusOrderByReportVersionDesc(
            UUID requestId, DiagnosticReportValidationStatus validationStatus);

    Optional<DiagnosticReportJpaEntity> findByIdAndRequestId(UUID id, UUID requestId);

    Optional<DiagnosticReportJpaEntity> findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(
            UUID requestId);
}
