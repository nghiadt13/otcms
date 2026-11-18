package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.DoctorLaboratoryResultService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratoryDraftService;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorLaboratoryResultServiceImpl implements DoctorLaboratoryResultService {
    private final ClinicalOrderPreparationService care;
    private final ServiceOrderRepository orders;
    private final LaboratoryDraftService results;
    public DoctorLaboratoryResultServiceImpl(ClinicalOrderPreparationService care, ServiceOrderRepository orders, LaboratoryDraftService results) {
        this.care = care; this.orders = orders; this.results = results;
    }
    @Override @Transactional(readOnly = true)
    public List<Result> list(UUID visitId, UUID doctorId) {
        care.requireCurrentCare(visitId, doctorId);
        return orders.findVisitOrdersByType(visitId, ClinicalServiceType.LABORATORY).stream()
                .filter(order -> order.getOrderStatus() == ServiceOrderStatus.RESULTS_AVAILABLE)
                .map(order -> new Result(order.getId(), order.getServiceNameSnapshot(), results.issued(order.getId()),
                        order.getDoctorResultAssessment() == null ? null : new Assessment(order.getDoctorResultAssessment(),
                                order.getDoctorResultAssessedByAccountId(), order.getDoctorResultAssessedAt())))
                .filter(result -> !result.values().isEmpty()).toList();
    }
    @Override @Transactional
    public Assessment assess(UUID visitId, UUID orderId, UUID doctorId, UUID accountId, String text) {
        care.requireCurrentCare(visitId, doctorId);
        if (text == null || text.isBlank() || text.trim().length() > 4000)
            throw new fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException("MSG-39");
        var order = orders.findById(orderId).orElseThrow(() -> new fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException("MSG-41"));
        if (!visitId.equals(order.getVisitId()) || order.getServiceType() != ClinicalServiceType.LABORATORY
                || order.getOrderStatus() != ServiceOrderStatus.RESULTS_AVAILABLE || results.issued(orderId).isEmpty())
            throw new fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException("MSG-41");
        var assessment = text.trim();
        var assessedAt = Instant.now();
        if (orders.recordDoctorResultAssessment(orderId, visitId, assessment, accountId, assessedAt) != 1)
            throw new fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException("MSG-42");
        return new Assessment(assessment, accountId, assessedAt);
    }
}
