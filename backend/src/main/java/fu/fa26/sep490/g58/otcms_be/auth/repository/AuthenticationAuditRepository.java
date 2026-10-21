package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthenticationAuditJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.UUID;

public interface AuthenticationAuditRepository extends JpaRepository<AuthenticationAuditJpaEntity, UUID> {
    Page<AuthenticationAuditJpaEntity> findAllByOccurredAtBefore(Instant cutoff, Pageable pageable);
    long countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(String method, String outcome,
                                                                      String identifierHmac, Instant since);
}
