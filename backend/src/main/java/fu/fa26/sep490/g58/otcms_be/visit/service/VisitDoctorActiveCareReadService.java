package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Current treatment facts only: OPEN visits with this Doctor's IN_PROGRESS assignments. */
public interface VisitDoctorActiveCareReadService {
    List<DoctorActiveVisit> findActiveVisits(UUID doctorProfileId);

    /** {@code examStatus} and {@code queueNumber} come from the exam queue; both are null when the visit has no entry. */
    record DoctorActiveVisit(UUID visitId, UUID patientProfileId, String visitSource, UUID linkedAppointmentId,
            UUID assignmentId, String stepCode, Instant startedAt, Instant expectedEnd, VisitExamStatus examStatus,
            Integer queueNumber) { }
}
