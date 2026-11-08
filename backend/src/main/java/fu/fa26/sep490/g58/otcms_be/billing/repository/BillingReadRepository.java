package fu.fa26.sep490.g58.otcms_be.billing.repository;

import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Billing-only persisted snapshots and confirmed receipts. No catalog/current-price joins. */
public interface BillingReadRepository extends Repository<InvoiceJpaEntity, UUID> {
    interface PaymentRow {
        UUID getId();
        UUID getInvoiceId();
        UUID getPatientId();
        String getInvoiceNumber();
        BigDecimal getAmount();
        String getCurrency();
        String getChannel();
        Instant getConfirmedAt();
    }
    interface BalanceRow {
        UUID getId();
        UUID getPatientId();
        String getInvoiceNumber();
        Instant getIssuedAt();
        BigDecimal getTotalAmount();
        BigDecimal getPaidAmount();
    }
    String PAYMENT_SCOPE = """
            from PaymentAttemptJpaEntity p, InvoiceJpaEntity i
            where p.invoiceId = i.id
                and p.status = fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus.CONFIRMED
                and (:staff = true or i.patientProfileId in :profiles)
                and (:fromTime is null or p.confirmedAt >= :fromTime)
                and (:toTime is null or p.confirmedAt < :toTime)
            """;
    @Query(value = """
            select p.id as id, i.id as invoiceId, i.patientProfileId as patientId, i.invoiceNumber as invoiceNumber,
                p.amount as amount, p.currencyCode as currency, cast(p.channel as String) as channel,
                p.confirmedAt as confirmedAt
            """ + PAYMENT_SCOPE + " order by p.confirmedAt desc, p.id desc",
            countQuery = "select count(p.id) " + PAYMENT_SCOPE)
    Page<PaymentRow> completedPayments(@Param("staff") boolean staff, @Param("profiles") Collection<UUID> profiles,
            @Param("fromTime") Instant from, @Param("toTime") Instant to, Pageable pageable);

    String TOTAL = "(select coalesce(sum(l.unitPriceSnapshot * l.quantity), 0) from InvoiceItemJpaEntity l where l.invoiceId = i.id)";
    String PAID = "(select coalesce(sum(p.amount), 0) from PaymentAttemptJpaEntity p where p.invoiceId = i.id"
            + " and p.status = fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus.CONFIRMED)";
    String UNPAID = " from InvoiceJpaEntity i where i.status = fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus.OPEN"
            + " and " + TOTAL + " > " + PAID;
    @Query(value = "select i.id as id, i.patientProfileId as patientId, i.invoiceNumber as invoiceNumber,"
            + " i.issuedAt as issuedAt, " + TOTAL + " as totalAmount, " + PAID + " as paidAmount"
            + UNPAID + " order by i.issuedAt asc, i.id asc", countQuery = "select count(i.id)" + UNPAID)
    Page<BalanceRow> unpaidInvoices(Pageable pageable);
}
