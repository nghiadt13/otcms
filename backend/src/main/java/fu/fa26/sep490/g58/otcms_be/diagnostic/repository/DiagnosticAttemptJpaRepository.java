package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAttemptJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticAttemptJpaRepository extends JpaRepository<DiagnosticAttemptJpaEntity, UUID> {
    List<DiagnosticAttemptJpaEntity> findAllByRequestIdOrderByAttemptNumberAsc(UUID requestId);

    Optional<DiagnosticAttemptJpaEntity> findByFirstUseEventId(UUID firstUseEventId);

    Optional<DiagnosticAttemptJpaEntity> findByRequestIdAndId(UUID requestId, UUID id);

    boolean existsByRequestIdAndStudyRefIsNotNull(UUID requestId);
}
