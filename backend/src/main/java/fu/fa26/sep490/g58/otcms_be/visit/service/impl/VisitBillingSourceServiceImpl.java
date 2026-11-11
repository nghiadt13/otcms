package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class VisitBillingSourceServiceImpl implements VisitBillingSourceService {
    private final ClinicVisitJpaRepository visits;
    private final VisitExamQueueService examQueue;

    public VisitBillingSourceServiceImpl(ClinicVisitJpaRepository visits, VisitExamQueueService examQueue) {
        this.visits = visits;
        this.examQueue = examQueue;
    }

    @Override
    @Transactional(readOnly = true)
    public BillingSource requireBillableVisit(UUID visitId) {
        var visit = visitId == null ? null : visits.findById(visitId).orElse(null);
        if (visit == null) {
            throw new VisitOperationException("VISIT_NOT_BILLABLE");
        }
        if ("CLOSED".equals(visit.getStatus())) {
            // A cancelled walk-in, or one closed before its fee was paid, must not collect money.
            VisitExamStatus status = examQueue.find(visitId).map(ExamQueueEntry::examStatus).orElse(null);
            if (status == VisitExamStatus.CANCELLED || status == VisitExamStatus.PENDING_PAYMENT) {
                throw new VisitOperationException("VISIT_NOT_BILLABLE");
            }
            return new BillingSource(visit.getId(), visit.getPatientProfileId(), null);
        }
        UUID feeServiceId = examQueue.requiredFeeServiceId(visitId)
                .orElseThrow(() -> new VisitOperationException("VISIT_NOT_BILLABLE"));
        return new BillingSource(visit.getId(), visit.getPatientProfileId(), feeServiceId);
    }
}
