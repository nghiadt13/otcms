package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorActiveCareReadService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VisitDoctorActiveCareReadServiceImpl implements VisitDoctorActiveCareReadService {
    private final VisitCareAssignmentRepository assignments;

    public VisitDoctorActiveCareReadServiceImpl(VisitCareAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    public List<DoctorActiveVisit> findActiveVisits(UUID doctorProfileId) {
        if (doctorProfileId == null) throw new IllegalArgumentException("Doctor profile ID is required");
        return assignments.findDoctorActiveCare(doctorProfileId).stream()
                .map(row -> new DoctorActiveVisit(row.getVisitId(), row.getPatientProfileId(), row.getVisitSource(),
                        row.getLinkedAppointmentId(), row.getAssignmentId(), row.getStepCode(), row.getStartedAt(),
                        row.getExpectedEnd(),
                        row.getExamStatus() == null ? null : VisitExamStatus.valueOf(row.getExamStatus()),
                        row.getQueueNumber()))
                .toList();
    }
}
