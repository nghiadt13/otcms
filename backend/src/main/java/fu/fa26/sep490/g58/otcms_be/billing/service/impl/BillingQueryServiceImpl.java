package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingQueryServiceImpl implements BillingQueryService {
    private final InvoiceItemRepository items;

    public BillingQueryServiceImpl(InvoiceItemRepository items) { this.items = items; }

    @Override
    @Transactional(readOnly = true)
    public boolean isServiceOrderPaid(UUID serviceOrderId) {
        return serviceOrderId != null && items.existsPaidItemOfServiceOrder(serviceOrderId);
    }
}
