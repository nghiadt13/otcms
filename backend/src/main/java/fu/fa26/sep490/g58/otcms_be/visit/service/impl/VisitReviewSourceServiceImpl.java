package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitReviewSourceRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class VisitReviewSourceServiceImpl implements VisitReviewSourceService {
    private final VisitReviewSourceRepository visits;
    public VisitReviewSourceServiceImpl(VisitReviewSourceRepository visits) { this.visits = visits; }
    public Optional<Source> find(UUID visitId) { return visits.find(visitId).map(this::source); }
    public List<Source> closedVisits(UUID patientProfileId) {
        return visits.closedVisits(patientProfileId).stream().map(this::source).toList();
    }
    private Source source(VisitReviewSourceRepository.Row row) {
        return new Source(row.id(), row.patientProfileId(), row.status(), row.arrivedAt(), "", "",
                row.appointmentId(), row.doctorId());
    }
}
