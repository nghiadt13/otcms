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

    /**
     * Internal (no controller): opens the OPEN invoice with the single exam-fee item of a just booked appointment, in
     * the booking transaction (D7: booking never waits for payment). Does nothing for a free service, a service that
     * cannot be billed, or an appointment that already has an invoice.
     */
    void openForBooking(UUID appointmentId, UUID createdByAccountId);

    /**
     * Internal (no controller): opens the OPEN invoice for a just submitted clinical ordering round so patients can
     * pay service orders online via PayOS immediately without waiting for reception. Does nothing when the round
     * already has an invoice or has no billable orders.
     */
    void openForClinicalRound(UUID roundId, UUID createdByAccountId);
}
