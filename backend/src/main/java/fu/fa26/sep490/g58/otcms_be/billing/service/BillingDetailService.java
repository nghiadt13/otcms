package fu.fa26.sep490.g58.otcms_be.billing.service;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService.ResultPage;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read side of UC-53 (payment details), UC-56 (one payment), UC-57 (unpaid items) and UC-58 (receipt). */
public interface BillingDetailService {
    /** One confirmed payment with the items it settled. Patient: own and approved dependents only; MSG-50 otherwise. */
    PaymentDetail payment(AuthenticatedAccount actor, UUID paymentId);

    /** The receipt of a confirmed payment (receptionist only; MSG-50 when missing). Same data as {@link #payment}. */
    PaymentDetail receipt(AuthenticatedAccount actor, UUID paymentId);

    /** The receipt as a PDF document (UC-58); MSG-04 when it cannot be prepared. */
    byte[] receiptPdf(AuthenticatedAccount actor, UUID paymentId);

    /** Items waiting for payment, oldest first. Staff only; invalid criteria are MSG-54. */
    ResultPage<UnpaidItem> unpaidItems(AuthenticatedAccount actor, UUID patientId, UUID serviceId, LocalDate from,
            LocalDate to, int page, int size);

    /** A patient's invoices with each item's status, those with something left to pay first. Staff only. */
    PatientBilling patientBilling(AuthenticatedAccount actor, UUID patientProfileId, int page, int size);

    /**
     * {@code method} is CASH for the counter or PAYOS; {@code receivedByName} is the receptionist of a counter payment,
     * {@code providerTransactionReference} the PayOS reference. {@code refundReason} of a line is shown to admin/owner only.
     */
    record PaymentDetail(UUID id, String receiptNumber, UUID invoiceId, String invoiceNumber, UUID patientProfileId,
            String patientName, BigDecimal amount, String currency, String method, String channel, Instant confirmedAt,
            String receivedByName, String providerTransactionReference, List<Line> items) { }

    record Line(UUID itemId, int lineNumber, String description, int quantity, BigDecimal unitPrice,
            BigDecimal lineTotal, String status, String refundReason) { }

    /** {@code status} is PENDING (payable) or PAYMENT_PENDING (an online/QR checkout is open for it). */
    record UnpaidItem(UUID itemId, UUID invoiceId, String invoiceNumber, UUID patientProfileId, String patientName,
            String sourceType, UUID visitId, UUID appointmentId, UUID serviceId, String description, int quantity,
            BigDecimal unitPrice, BigDecimal lineTotal, String status, Instant orderedAt) { }

    record PatientBilling(UUID patientProfileId, String patientName, ResultPage<InvoiceResponse> invoices) { }
}
