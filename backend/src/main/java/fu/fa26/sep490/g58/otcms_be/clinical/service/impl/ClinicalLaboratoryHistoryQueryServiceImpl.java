package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalLaboratoryHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratoryDraftService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalLaboratoryHistoryQueryServiceImpl implements ClinicalLaboratoryHistoryQueryService {
    private final ServiceOrderRepository orders;
    private final LaboratoryDraftService results;

    public ClinicalLaboratoryHistoryQueryServiceImpl(ServiceOrderRepository orders, LaboratoryDraftService results) {
        this.orders = orders;
        this.results = results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LaboratoryResultSummary> findReleased(Collection<UUID> visitIds) {
        if (visitIds == null || visitIds.size() > 50 || visitIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid historical visit scope");
        }
        var summaries = new ArrayList<LaboratoryResultSummary>();
        for (UUID visitId : new LinkedHashSet<>(visitIds)) {
            for (var order : orders.findVisitOrdersByType(visitId, ClinicalServiceType.LABORATORY)) {
                if (order.getOrderStatus() != ServiceOrderStatus.RESULTS_AVAILABLE) continue;
                var values = results.issued(order.getId());
                if (!values.isEmpty()) summaries.add(new LaboratoryResultSummary(visitId,
                        order.getServiceNameSnapshot(), values));
            }
        }
        return List.copyOf(summaries);
    }
}
