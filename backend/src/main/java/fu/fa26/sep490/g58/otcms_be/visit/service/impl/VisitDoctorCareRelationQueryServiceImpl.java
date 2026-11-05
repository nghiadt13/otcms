package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorCareRelationQueryService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VisitDoctorCareRelationQueryServiceImpl implements VisitDoctorCareRelationQueryService {
    private final VisitCareAssignmentRepository assignments;
    public VisitDoctorCareRelationQueryServiceImpl(VisitCareAssignmentRepository assignments) { this.assignments = assignments; }
    @Override public boolean hasCurrentCare(UUID doctorProfileId, UUID patientProfileId) {
        return doctorProfileId != null && patientProfileId != null && assignments.hasCurrentDoctorCare(doctorProfileId, patientProfileId);
    }
}
