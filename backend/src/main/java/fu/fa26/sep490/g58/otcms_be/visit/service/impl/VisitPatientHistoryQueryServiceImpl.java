package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitPatientHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class VisitPatientHistoryQueryServiceImpl implements VisitPatientHistoryQueryService {
    private final ClinicVisitJpaRepository visits;
    public VisitPatientHistoryQueryServiceImpl(ClinicVisitJpaRepository visits) { this.visits = visits; }
    @Override public HistoryPage findPastVisits(UUID patientProfileId, UUID currentVisitId, int page, int size) {
        if (patientProfileId == null || currentVisitId == null || page < 0 || size < 1 || size > 50) throw new VisitOperationException("VALIDATION_FAILED");
        var current = visits.findById(currentVisitId).orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        if (!patientProfileId.equals(current.getPatientProfileId()) || !"OPEN".equals(current.getStatus())) throw new VisitOperationException("ACCESS_DENIED");
        var history = visits.findHistoricalVisits(patientProfileId, currentVisitId, current.getArrivedAt(), PageRequest.of(page, size));
        return new HistoryPage(history.getContent().stream().map(v -> new HistoricalVisit(v.getId(), v.getPatientProfileId(), v.getArrivedAt(), v.getStatus())).toList(), history.getTotalElements());
    }
}
