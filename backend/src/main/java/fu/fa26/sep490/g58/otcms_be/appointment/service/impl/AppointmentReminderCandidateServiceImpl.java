package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentJpaRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentReminderCandidateService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentReminderCandidateServiceImpl implements AppointmentReminderCandidateService {
    private static final String CONFIRMED = "CONFIRMED";
    private static final String NO_SHOW = "NO_SHOW";
    private static final String DOCTOR_FOLLOW_UP = "DOCTOR_FOLLOW_UP";

    private final AppointmentJpaRepository appointments;
    private final PatientProfileDirectory profiles;

    public AppointmentReminderCandidateServiceImpl(AppointmentJpaRepository appointments,
            PatientProfileDirectory profiles) {
        this.appointments = appointments;
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> upcoming(Instant from, Instant until) {
        return map(appointments.findByStatusAndStartTimeGreaterThanAndStartTimeLessThanEqualOrderByStartTimeAscIdAsc(
                CONFIRMED, from, until));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> overdueFollowUps(Instant now, Instant since) {
        return map(appointments.findByBookingSourceAndStatusAndStartTimeLessThanAndStartTimeGreaterThanEqualOrderByStartTimeAscIdAsc(
                DOCTOR_FOLLOW_UP, NO_SHOW, now, since));
    }

    private List<Candidate> map(List<AppointmentJpaEntity> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, String> patientNames = profiles.findProfiles(
                        rows.stream().map(AppointmentJpaEntity::getPatientProfileId).distinct().toList()).stream()
                .collect(Collectors.toMap(PatientProfileDirectory.ProfileRecord::id,
                        PatientProfileDirectory.ProfileRecord::fullName, (first, ignored) -> first));
        return rows.stream().filter(row -> patientNames.containsKey(row.getPatientProfileId()))
                .map(row -> new Candidate(row.getId(), row.getPatientProfileId(),
                        patientNames.get(row.getPatientProfileId()), DOCTOR_FOLLOW_UP.equals(row.getBookingSource()),
                        row.getStartTime()))
                .toList();
    }
}
