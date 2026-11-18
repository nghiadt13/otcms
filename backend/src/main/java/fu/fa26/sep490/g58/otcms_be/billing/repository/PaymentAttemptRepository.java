package fu.fa26.sep490.g58.otcms_be.billing.repository;

import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptJpaEntity, UUID> {
    interface RoundPaymentTime {
        UUID getRoundId();
        java.time.Instant getConfirmedAt();
    }

    @Query("select invoice.clinicalOrderingRoundId as roundId, max(payment.confirmedAt) as confirmedAt "
            + "from InvoiceJpaEntity invoice, PaymentAttemptJpaEntity payment "
            + "where payment.invoiceId = invoice.id and invoice.clinicalOrderingRoundId in :roundIds "
            + "and invoice.status = fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus.PAID "
            + "and payment.status = fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus.CONFIRMED "
            + "group by invoice.clinicalOrderingRoundId")
    List<RoundPaymentTime> findConfirmedRoundPaymentTimes(@Param("roundIds") java.util.Collection<UUID> roundIds);
    Optional<PaymentAttemptJpaEntity> findFirstByInvoiceIdAndChannelAndStatusOrderByCreatedAtDesc(
            UUID invoiceId, fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel channel,
            PaymentAttemptStatus status);
    Optional<PaymentAttemptJpaEntity> findFirstByCreatedByAccountIdAndIdempotencyKey(UUID actorId, String key);
    Optional<PaymentAttemptJpaEntity> findFirstByProviderOrderCode(Long orderCode);
    List<PaymentAttemptJpaEntity> findAllByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    @Query("select coalesce(sum(payment.amount), 0) from PaymentAttemptJpaEntity payment "
            + "where payment.invoiceId = :invoiceId and payment.status = :status")
    BigDecimal sumByInvoiceAndStatus(@Param("invoiceId") UUID invoiceId,
            @Param("status") PaymentAttemptStatus status);
}
