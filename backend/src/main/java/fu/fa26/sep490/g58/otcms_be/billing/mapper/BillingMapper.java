package fu.fa26.sep490.g58.otcms_be.billing.mapper;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BillingMapper {
    public InvoiceResponse toInvoice(InvoiceJpaEntity invoice, List<InvoiceItemJpaEntity> items,
            BigDecimal paidAmount) {
        List<InvoiceResponse.Item> responseItems = items.stream().map(item -> new InvoiceResponse.Item(
                item.getLineNumber(), item.getServiceId(), item.getDescriptionSnapshot(), item.getQuantity(),
                item.getUnitPriceSnapshot(), item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()))
        )).toList();
        BigDecimal total = responseItems.stream().map(InvoiceResponse.Item::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getPatientProfileId(),
                invoice.getVisitId(), invoice.getAppointmentId(), invoice.getStatus().name(), total,
                paid, total.subtract(paid).max(BigDecimal.ZERO), invoice.getIssuedAt(), responseItems);
    }

    public PaymentAttemptResponse toPaymentAttempt(PaymentAttemptJpaEntity attempt) {
        return new PaymentAttemptResponse(attempt.getId(), attempt.getInvoiceId(), attempt.getChannel().name(),
                attempt.getProvider(), attempt.getAmount(), attempt.getCurrencyCode(), attempt.getStatus().name(),
                attempt.getCheckoutUrl(), attempt.getQrCode(), attempt.getExpiresAt(), attempt.getCreatedAt(),
                attempt.getConfirmedAt(), attempt.getReconciliationReason());
    }
}
