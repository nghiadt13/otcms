package fu.fa26.sep490.g58.otcms_be.billing.service;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CreateVisitInvoiceRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoicePageResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;

public interface InvoiceService {
    InvoiceResponse createForVisit(UUID visitId, CreateVisitInvoiceRequest request, AuthenticatedAccount actor);
    InvoiceResponse createForAppointment(UUID appointmentId, AuthenticatedAccount actor);
    InvoiceResponse get(UUID invoiceId, AuthenticatedAccount actor);
    InvoicePageResponse listMine(AuthenticatedAccount actor, int page, int size);
}
