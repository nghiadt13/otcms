package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticHistoryAccessAuditJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticHistoryAccessAuditJpaRepository
        extends JpaRepository<DiagnosticHistoryAccessAuditJpaEntity, UUID> {
}
