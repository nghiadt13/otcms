package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Read-only count of patient self-reschedules recorded in appointment audit logs. */
public interface AppointmentRescheduleCountQueryService {
    int MAX_PATIENT_RESCHEDULES = 2;

    int countPatientReschedules(UUID appointmentId);

    Map<UUID, Integer> countPatientReschedules(Collection<UUID> appointmentIds);
}
