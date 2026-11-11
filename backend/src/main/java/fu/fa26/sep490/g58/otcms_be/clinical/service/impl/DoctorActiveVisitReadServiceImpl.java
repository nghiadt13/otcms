package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.DoctorActiveVisitDtos.*;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.DoctorActiveVisitReadService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorActiveCareReadService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorActiveVisitReadServiceImpl implements DoctorActiveVisitReadService {
    private final ClinicalDoctorIdentityResolver doctors;
    private final VisitDoctorActiveCareReadService visits;
    private final PatientClinicalIdentityQueryService patients;

    public DoctorActiveVisitReadServiceImpl(ClinicalDoctorIdentityResolver doctors,
            VisitDoctorActiveCareReadService visits, PatientClinicalIdentityQueryService patients) {
        this.doctors = doctors;
        this.visits = visits;
        this.patients = patients;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ActiveVisits findActiveVisits(AuthenticatedAccount account) {
        var active = visits.findActiveVisits(doctors.requireDoctorProfile(account));
        if (active.isEmpty()) return new ActiveVisits(List.of());
        var ids = active.stream().map(VisitDoctorActiveCareReadService.DoctorActiveVisit::patientProfileId).distinct().toList();
        if (ids.contains(null)) throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE");
        var identities = patients.findClinicalIdentities(ids);
        var items = active.stream().map(visit -> {
            var patient = identities.get(visit.patientProfileId());
            if (patient == null || !visit.patientProfileId().equals(patient.patientProfileId())) {
                throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE");
            }
            return new ActiveVisit(visit.visitId(), visit.patientProfileId(),
                    new PatientIdentity(patient.fullName(), patient.dateOfBirth(), patient.gender()),
                    visit.visitSource(), visit.linkedAppointmentId(), visit.assignmentId(), visit.stepCode(),
                    visit.startedAt(), visit.expectedEnd(),
                    visit.examStatus() == null ? null : visit.examStatus().name(), visit.queueNumber());
        }).toList();
        return new ActiveVisits(items);
    }
}
