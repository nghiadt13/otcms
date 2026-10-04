package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.entity.AuthenticationAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.UUID;
import java.time.Instant;

public interface AuthenticationAuditRepository extends JpaRepository<AuthenticationAudit, UUID> {
    Page<AuthenticationAudit> findAllByOccurredAtBefore(Instant cutoff, Pageable pageable);
    long countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(String method, String outcome,
                                                                      String identifierHmac, Instant since);
}
