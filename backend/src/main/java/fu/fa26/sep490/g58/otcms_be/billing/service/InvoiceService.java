package fu.fa26.sep490.g58.otcms_be.billing.service;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CreateVisitInvoiceRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoicePageResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;

public interface InvoiceService {
    InvoiceResponse createForClinicalRound(UUID roundId, AuthenticatedAccount actor);
    InvoiceResponse createForVisit(UUID visitId, CreateVisitInvoiceRequest request, AuthenticatedAccount actor);
    InvoiceResponse createForAppointment(UUID appointmentId, AuthenticatedAccount actor);
    InvoiceResponse get(UUID invoiceId, AuthenticatedAccount actor);
    InvoicePageResponse listMine(AuthenticatedAccount actor, int page, int size);

    enum VoidResult {
        /** OPEN invoice without money was voided and its PENDING attempts closed. */
        VOIDED,
        /** Invoice was already VOID (idempotent repeat). */
        ALREADY_VOID,
        /** The appointment has no invoice. */
        NO_INVOICE,
        /** Money was already confirmed (invoice PAID or partially paid): never voided. */
        HAS_PAYMENT
    }

    /**
     * Internal (no controller): voids the unpaid invoice of an appointment hold under the invoice row lock.
     * Does not call the payment gateway; see {@link AppointmentHoldSettlementService} for the full release flow.
     */
    VoidResult voidUnpaidAppointmentInvoice(UUID appointmentId);
}
