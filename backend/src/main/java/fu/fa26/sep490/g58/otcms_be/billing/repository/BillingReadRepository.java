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
    interface RefundRow {
        UUID getItemId();
        UUID getInvoiceId();
        UUID getPatientId();
        String getInvoiceNumber();
        String getDescription();
        BigDecimal getUnitPrice();
        int getQuantity();
        String getRefundStatus();
        String getRefundReason();
        Instant getRefundedAt();
        UUID getRefundedByAccountId();
    }
    String PAYMENT_SCOPE = """
            from PaymentAttemptJpaEntity p, InvoiceJpaEntity i
            where p.invoiceId = i.id
                and p.status = fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus.CONFIRMED
                and (:staff = true or i.patientProfileId in :profiles)
                and (cast(:fromTime as Instant) is null or p.confirmedAt >= :fromTime)
                and (cast(:toTime as Instant) is null or p.confirmedAt < :toTime)
            """;
    @Query(value = """
            select p.id as id, i.id as invoiceId, i.patientProfileId as patientId, i.invoiceNumber as invoiceNumber,
                p.amount as amount, p.currencyCode as currency, cast(p.channel as String) as channel,
                p.confirmedAt as confirmedAt
            """ + PAYMENT_SCOPE + " order by p.confirmedAt desc, p.id desc",
            countQuery = "select count(p.id) " + PAYMENT_SCOPE)
    Page<PaymentRow> completedPayments(@Param("staff") boolean staff, @Param("profiles") Collection<UUID> profiles,
            @Param("fromTime") Instant from, @Param("toTime") Instant to, Pageable pageable);

    String REFUNDS = " from InvoiceItemJpaEntity l, InvoiceJpaEntity i where i.id = l.invoiceId and l.refundStatus = :status";
    /** Items waiting for / done with a manual refund (status REFUND_PENDING or REFUNDED), oldest invoice first. */
    @Query(value = "select l.id as itemId, i.id as invoiceId, i.patientProfileId as patientId, i.invoiceNumber as invoiceNumber,"
            + " l.descriptionSnapshot as description, l.unitPriceSnapshot as unitPrice, l.quantity as quantity,"
            + " l.refundStatus as refundStatus, l.refundReason as refundReason, l.refundedAt as refundedAt,"
            + " l.refundedByAccountId as refundedByAccountId" + REFUNDS + " order by i.issuedAt asc, l.lineNumber asc",
            countQuery = "select count(l.id)" + REFUNDS)
    Page<RefundRow> refunds(@Param("status") String status, Pageable pageable);

    // Void items are not owed: they leave the total (same rule as the invoice response).
    String TOTAL = "(select coalesce(sum(l.unitPriceSnapshot * l.quantity), 0) from InvoiceItemJpaEntity l where l.invoiceId = i.id and l.voidedAt is null)";
    String PAID = "(select coalesce(sum(p.amount), 0) from PaymentAttemptJpaEntity p where p.invoiceId = i.id"
            + " and p.status = fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus.CONFIRMED)";
    String UNPAID = " from InvoiceJpaEntity i where i.status = fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus.OPEN"
            + " and " + TOTAL + " > " + PAID;
    @Query(value = "select i.id as id, i.patientProfileId as patientId, i.invoiceNumber as invoiceNumber,"
            + " i.issuedAt as issuedAt, " + TOTAL + " as totalAmount, " + PAID + " as paidAmount"
            + UNPAID + " order by i.issuedAt asc, i.id asc", countQuery = "select count(i.id)" + UNPAID)
    Page<BalanceRow> unpaidInvoices(Pageable pageable);

    String OPEN_INVOICE = "i.status = fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus.OPEN";
    String UNPAID_ITEMS = " from InvoiceItemJpaEntity l join InvoiceJpaEntity i on i.id = l.invoiceId"
            + " left join PaymentAttemptJpaEntity a on a.id = l.paymentAttemptId"
            + " where " + OPEN_INVOICE + " and l.voidedAt is null and l.refundStatus is null"
            + " and (a.id is null or a.status <> fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus.CONFIRMED)"
            + " and (:anyPatient = true or i.patientProfileId = :patientId)"
            + " and (:anyService = true or l.serviceId = :serviceId)"
            + " and i.issuedAt >= :fromTime and i.issuedAt < :toTime";
    /** Rows {item, invoice, attempt-or-null} of items still to be paid (UC57), oldest order first. A criterion is ignored when its any-flag is true; callers pass placeholder ids and an open-ended period instead of null (Postgres cannot type a null parameter).  */
    @Query(value = "select l, i, a" + UNPAID_ITEMS + " order by i.issuedAt asc, l.lineNumber asc",
            countQuery = "select count(l.id)" + UNPAID_ITEMS)
    Page<Object[]> unpaidItems(@Param("anyPatient") boolean anyPatient, @Param("patientId") UUID patientId,
            @Param("anyService") boolean anyService, @Param("serviceId") UUID serviceId,
            @Param("fromTime") Instant from, @Param("toTime") Instant to, Pageable pageable);

    /** Invoices of one patient (UC53): those with something left to pay first, then newest. Voided holds are hidden. */
    @Query(value = "select i from InvoiceJpaEntity i where i.patientProfileId = :profileId"
            + " and i.status <> fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus.VOID"
            + " order by case when " + OPEN_INVOICE + " and " + TOTAL + " > " + PAID + " then 0 else 1 end,"
            + " i.issuedAt desc, i.id",
            countQuery = "select count(i.id) from InvoiceJpaEntity i where i.patientProfileId = :profileId"
                    + " and i.status <> fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus.VOID")
    Page<InvoiceJpaEntity> patientInvoices(@Param("profileId") UUID profileId, Pageable pageable);
}
