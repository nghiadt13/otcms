package fu.fa26.sep490.g58.otcms_be.billing.repository;

import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItemJpaEntity, InvoiceItemJpaEntity.Key> {
    List<InvoiceItemJpaEntity> findAllByInvoiceIdOrderByLineNumber(UUID invoiceId);
    Optional<InvoiceItemJpaEntity> findFirstById(UUID id);
    /** Items a payment settled (the receipt lines). */
    List<InvoiceItemJpaEntity> findAllByPaymentAttemptIdOrderByLineNumber(UUID paymentAttemptId);

    /**
     * Locks every item of one invoice (SELECT ... FOR UPDATE) in line order. Call it only after the invoice row is
     * locked: the lock order is always invoice, then items, then attempts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvoiceItemJpaEntity i where i.invoiceId = :invoiceId order by i.lineNumber")
    List<InvoiceItemJpaEntity> lockAllByInvoice(@Param("invoiceId") UUID invoiceId);

    /** A paid item that still stands: CONFIRMED attempt, not void, no refund asked or done. */
    @Query("select count(i) > 0 from InvoiceItemJpaEntity i, PaymentAttemptJpaEntity a where a.id = i.paymentAttemptId"
            + " and i.serviceOrderId = :serviceOrderId and i.voidedAt is null and i.refundStatus is null"
            + " and a.status = fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus.CONFIRMED")
    boolean existsPaidItemOfServiceOrder(@Param("serviceOrderId") UUID serviceOrderId);
}
