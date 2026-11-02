package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.Optional;
import java.util.UUID;

/** Historical labels for a visit's originating appointment. */
public interface AppointmentReviewContextService {
    record Context(String doctorName, String serviceName) { }
    Optional<Context> find(UUID appointmentId);
}
