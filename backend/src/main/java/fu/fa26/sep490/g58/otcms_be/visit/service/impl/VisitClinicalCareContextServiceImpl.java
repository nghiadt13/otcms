package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VisitClinicalCareContextServiceImpl implements VisitClinicalCareContextService {
    private final ClinicVisitJpaRepository visits;
    private final VisitCareAssignmentRepository assignments;

    public VisitClinicalCareContextServiceImpl(ClinicVisitJpaRepository visits,
            VisitCareAssignmentRepository assignments) {
        this.visits = visits;
        this.assignments = assignments;
    }

    @Override
    public Optional<ClinicalCareContext> findClinicalCareContext(UUID visitId) {
        if (visitId == null) return Optional.empty();
        return visits.findById(visitId).map(visit -> {
            var assignment = assignments.findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS");
            UUID assignmentId = assignment.map(value -> value.getId()).orElse(null);
            UUID doctorId = assignment.map(value -> value.getDoctorProfileId()).orElse(null);
            boolean open = "OPEN".equals(visit.getStatus());
            return new ClinicalCareContext(visit.getId(), visit.getPatientProfileId(), assignmentId,
                    doctorId, open, open && assignment.isPresent() && doctorId != null);
        });
    }
}
