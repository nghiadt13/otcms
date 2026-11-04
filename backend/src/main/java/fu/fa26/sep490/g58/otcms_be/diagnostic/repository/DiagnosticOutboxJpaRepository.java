package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DiagnosticOutboxJpaRepository extends JpaRepository<DiagnosticOutboxJpaEntity, UUID> {
    Optional<DiagnosticOutboxJpaEntity> findByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from DiagnosticOutboxJpaEntity event
            where event.status = :status and event.nextAttemptAt <= :now
            order by event.nextAttemptAt asc, event.createdAt asc
            """)
    List<DiagnosticOutboxJpaEntity> lockDueEvents(
            @Param("status") DiagnosticOutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable);
}
