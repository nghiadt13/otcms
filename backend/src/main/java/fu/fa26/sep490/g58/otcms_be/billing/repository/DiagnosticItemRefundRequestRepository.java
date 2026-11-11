package fu.fa26.sep490.g58.otcms_be.billing.repository;

import fu.fa26.sep490.g58.otcms_be.billing.model.entity.DiagnosticItemRefundRequestJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DiagnosticItemRefundRequestRepository extends JpaRepository<DiagnosticItemRefundRequestJpaEntity, UUID> {
    Optional<DiagnosticItemRefundRequestJpaEntity> findFirstByRequestedByAccountIdAndIdempotencyKey(UUID actorId, String idempotencyKey);
    Optional<DiagnosticItemRefundRequestJpaEntity> findFirstByInvoiceIdAndLineNumberAndStatusIn(UUID invoiceId, int lineNumber, List<String> statuses);
    List<DiagnosticItemRefundRequestJpaEntity> findAllByStatusOrderByRequestedAtAscIdAsc(String status, Pageable pageable);
    List<DiagnosticItemRefundRequestJpaEntity> findAllByInvoiceIdAndStatus(UUID invoiceId, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from DiagnosticItemRefundRequestJpaEntity request where request.id = :id")
    Optional<DiagnosticItemRefundRequestJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
