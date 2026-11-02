package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentInvoicePaidStatusQueryService;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentInvoicePaidStatusQueryServiceImpl implements AppointmentInvoicePaidStatusQueryService {
    private final InvoiceRepository invoices;

    public AppointmentInvoicePaidStatusQueryServiceImpl(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Boolean> paidStatus(Collection<UUID> appointmentIds) {
        if (appointmentIds == null || appointmentIds.isEmpty()) return Map.of();
        return invoices.findAllByAppointmentIdIn(appointmentIds).stream()
                .filter(invoice -> invoice.getAppointmentId() != null)
                .collect(Collectors.toUnmodifiableMap(invoice -> invoice.getAppointmentId(),
                        invoice -> invoice.getStatus() == InvoiceStatus.PAID));
    }
}
