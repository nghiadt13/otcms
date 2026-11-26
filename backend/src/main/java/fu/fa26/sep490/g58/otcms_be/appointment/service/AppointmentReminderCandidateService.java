package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public appointment-owned read API for notification reminders. */
public interface AppointmentReminderCandidateService {
    record Candidate(UUID appointmentId, UUID patientProfileId, String patientName, boolean followUp, Instant startTime) { }

    List<Candidate> upcoming(Instant from, Instant until);
    List<Candidate> overdueFollowUps(Instant now, Instant since);
}
