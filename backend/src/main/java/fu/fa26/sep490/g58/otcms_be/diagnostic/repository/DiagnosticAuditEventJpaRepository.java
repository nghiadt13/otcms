package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAuditEventJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticAuditEventJpaRepository extends JpaRepository<DiagnosticAuditEventJpaEntity, UUID> {
    Optional<DiagnosticAuditEventJpaEntity> findByEventId(UUID eventId);

    List<DiagnosticAuditEventJpaEntity> findAllByRequestIdOrderByRecordedAtDesc(UUID requestId);

    List<DiagnosticAuditEventJpaEntity> findAllByVisitIdOrderByRecordedAtDesc(UUID visitId);
}
