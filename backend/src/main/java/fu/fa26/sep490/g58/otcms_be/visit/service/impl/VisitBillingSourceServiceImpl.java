package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitBillingSourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class VisitBillingSourceServiceImpl implements VisitBillingSourceService {
    private final ClinicVisitJpaRepository visits;

    public VisitBillingSourceServiceImpl(ClinicVisitJpaRepository visits) {
        this.visits = visits;
    }

    @Override
    @Transactional(readOnly = true)
    public BillingSource requireClosedVisit(UUID visitId) {
        var visit = visitId == null ? null : visits.findById(visitId).orElse(null);
        if (visit == null || !"CLOSED".equals(visit.getStatus())) {
            throw new VisitOperationException("VISIT_NOT_BILLABLE");
        }
        return new BillingSource(visit.getId(), visit.getPatientProfileId());
    }
}
