package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.util.UUID;

/** Internal Billing handoff after the entire invoice has been confirmed paid. */
public interface DiagnosticPaymentPublicationService {
    void publishPaidOrder(UUID eventId, UUID invoiceId, UUID serviceOrderId);
}
