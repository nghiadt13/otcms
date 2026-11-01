package fu.fa26.sep490.g58.otcms_be.billing.controller;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CreateVisitInvoiceRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoicePageResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InvoiceController {
    private final InvoiceService invoices;
    public InvoiceController(InvoiceService invoices) { this.invoices = invoices; }

    @PostMapping("/visits/{visitId}/invoices")
    public InvoiceResponse createForVisit(@PathVariable UUID visitId,
            @Valid @RequestBody CreateVisitInvoiceRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return invoices.createForVisit(visitId, request, actor);
    }

    @PostMapping("/appointments/{appointmentId}/invoices")
    public InvoiceResponse createForAppointment(@PathVariable UUID appointmentId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return invoices.createForAppointment(appointmentId, actor);
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse get(@PathVariable UUID invoiceId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return invoices.get(invoiceId, actor);
    }

    @GetMapping("/me/invoices")
    public InvoicePageResponse listMine(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return invoices.listMine(actor, page, size);
    }
}
