package fu.fa26.sep490.g58.otcms_be.billing.mapper;

import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceItemStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BillingMapper {
    /** {@code statuses} is keyed by item id; an item missing from it is reported as PENDING. */
    public InvoiceResponse toInvoice(InvoiceJpaEntity invoice, List<InvoiceItemJpaEntity> items,
            BigDecimal paidAmount, Map<UUID, InvoiceItemStatus> statuses) {
        List<InvoiceResponse.Item> responseItems = items.stream().map(item -> new InvoiceResponse.Item(
                item.getId(), item.getLineNumber(), item.getServiceId(), item.getDescriptionSnapshot(), item.getQuantity(),
                item.getUnitPriceSnapshot(), item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())),
                statuses.getOrDefault(item.getId(), InvoiceItemStatus.PENDING).name())).toList();
        BigDecimal total = responseItems.stream().filter(item -> !InvoiceItemStatus.VOID.name().equals(item.status()))
                .map(InvoiceResponse.Item::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getPatientProfileId(),
                invoice.getVisitId(), invoice.getAppointmentId(), invoice.getStatus().name(), total,
                paid, total.subtract(paid).max(BigDecimal.ZERO), invoice.getIssuedAt(), responseItems,
                invoice.getSourceType(), invoice.getClinicalOrderingRoundId());
    }

    public PaymentAttemptResponse toPaymentAttempt(PaymentAttemptJpaEntity attempt) {
        return new PaymentAttemptResponse(attempt.getId(), attempt.getInvoiceId(), attempt.getChannel().name(),
                attempt.getProvider(), attempt.getAmount(), attempt.getCurrencyCode(), attempt.getStatus().name(),
                attempt.getCheckoutUrl(), attempt.getQrCode(), attempt.getExpiresAt(), attempt.getCreatedAt(),
                attempt.getConfirmedAt(), attempt.getReconciliationReason(),
                attempt.getTransferBankBin(), attempt.getTransferAccountNumber(),
                attempt.getTransferAccountName(), attempt.getTransferDescription());
    }
}
