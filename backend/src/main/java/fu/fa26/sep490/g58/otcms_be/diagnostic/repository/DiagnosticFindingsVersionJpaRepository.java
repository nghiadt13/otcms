package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticFindingsVersionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticFindingsVersionJpaRepository
        extends JpaRepository<DiagnosticFindingsVersionJpaEntity, UUID> {
    List<DiagnosticFindingsVersionJpaEntity> findAllByRequestIdOrderByVersionNumberDesc(UUID requestId);

    Optional<DiagnosticFindingsVersionJpaEntity> findFirstByRequestIdOrderByVersionNumberDesc(UUID requestId);

    Optional<DiagnosticFindingsVersionJpaEntity> findByIdAndRequestId(UUID id, UUID requestId);

    Optional<DiagnosticFindingsVersionJpaEntity> findByRequestIdAndRequiredPdfReportId(UUID requestId, UUID reportId);

    Optional<DiagnosticFindingsVersionJpaEntity> findFirstByRequestIdAndStateOrderByVersionNumberDesc(
            UUID requestId, DiagnosticFindingsVersionState state);
}
