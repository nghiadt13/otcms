package fu.fa26.sep490.g58.otcms_be.billing.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public interface BillingReadService {
    ResultPage<Payment> payments(AuthenticatedAccount actor, UUID patientId, LocalDate from, LocalDate to, int page, int size);
    ResultPage<Balance> unpaid(AuthenticatedAccount actor, int page, int size);
    String invoiceExport(AuthenticatedAccount actor, UUID invoiceId);
    String receiptExport(AuthenticatedAccount actor, UUID paymentId);

    record ResultPage<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        public ResultPage { items = List.copyOf(items); }
    }
    record Payment(UUID id, UUID invoiceId, UUID patientId, String invoiceNumber, BigDecimal amount,
            String currency, String channel, Instant confirmedAt) { }
    record Balance(UUID id, UUID patientId, String invoiceNumber, Instant issuedAt, BigDecimal totalAmount,
            BigDecimal paidAmount, BigDecimal outstandingBalance) { }
}
