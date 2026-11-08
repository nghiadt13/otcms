package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalVisitCompletionEvidenceServiceImpl implements ClinicalVisitCompletionEvidenceService {
    private final ClinicalExaminationRepository examinations;
    private final ServiceOrderRepository orders;

    public ClinicalVisitCompletionEvidenceServiceImpl(ClinicalExaminationRepository examinations,
            ServiceOrderRepository orders) {
        this.examinations = examinations;
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalCompletionEvidence getCompletionEvidence(UUID visitId) {
        if (visitId == null) throw new ClinicalOperationException("MSG-39");

        var examination = examinations.findByVisitId(visitId);
        var activeOrders = orders.findByVisitId(visitId).stream()
                .filter(order -> order.getOrderStatus() != ServiceOrderStatus.CANCELLED)
                .toList();
        int outstandingCount = (int) activeOrders.stream()
                .filter(order -> order.getOrderStatus() == ServiceOrderStatus.ORDERED
                        || order.getPaymentStatus() != fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus.PAID)
                .count();
        var imagingOrderIds = activeOrders.stream()
                .filter(order -> order.getServiceType() == ClinicalServiceType.IMAGING)
                .sorted(Comparator.comparing(ServiceOrderJpaEntity::getCreatedAt)
                        .thenComparing(order -> order.getId().toString()))
                .map(ServiceOrderJpaEntity::getId)
                .toList();

        return new ClinicalCompletionEvidence(visitId, examination.isPresent(),
                examination.map(value -> value.getStatus() == ClinicalExaminationStatus.COMPLETED).orElse(false),
                activeOrders.size(), outstandingCount, activeOrders.stream().allMatch(
                        order -> order.getOrderStatus() == ServiceOrderStatus.RESULTS_AVAILABLE), imagingOrderIds);
    }
}
