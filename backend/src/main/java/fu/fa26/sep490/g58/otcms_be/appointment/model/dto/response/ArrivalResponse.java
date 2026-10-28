package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ArrivalResponse(UUID appointmentId, UUID patientProfileId, String source,
        String state, Instant arrivedAt, Instant recordedAt) { }
