package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitOrderQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalVisitOrderQueryServiceImpl implements ClinicalVisitOrderQueryService {
    private final ServiceOrderRepository orders;

    public ClinicalVisitOrderQueryServiceImpl(ServiceOrderRepository orders) { this.orders = orders; }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByVisit(UUID visitId) {
        // TODO Phase 09: use BillingQueryService.isServiceOrderPaid when available.
        return orders.findByVisitId(visitId).stream().map(order -> new Order(order.getId(), order.getOrderCode(),
                order.getServiceNameSnapshot(), order.getOrderStatus().name(), order.getPaymentStatus().name(),
                order.getRoomId())).toList();
    }
}
