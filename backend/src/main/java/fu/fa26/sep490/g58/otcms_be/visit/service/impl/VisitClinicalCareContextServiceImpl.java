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
    public java.util.Map<UUID, ClinicalCareContext> findClinicalCareContexts(java.util.Collection<UUID> visitIds) {
        if (visitIds.isEmpty()) return java.util.Map.of();
        var current = new java.util.HashMap<UUID, fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitCareAssignmentJpaEntity>();
        for (var row : assignments.findByVisitIdInAndStatusOrderByAssignedAtDescIdDesc(visitIds, "IN_PROGRESS")) {
            current.putIfAbsent(row.getVisitId(), row);
        }
        var result = new java.util.HashMap<UUID, ClinicalCareContext>();
        for (var visit : visits.findAllById(visitIds)) {
            var assignment = current.get(visit.getId());
            UUID doctorId = assignment == null ? null : assignment.getDoctorProfileId();
            boolean open = "OPEN".equals(visit.getStatus());
            result.put(visit.getId(), new ClinicalCareContext(visit.getId(), visit.getPatientProfileId(),
                    assignment == null ? null : assignment.getId(), doctorId, open, open && doctorId != null));
        }
        return java.util.Map.copyOf(result);
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
