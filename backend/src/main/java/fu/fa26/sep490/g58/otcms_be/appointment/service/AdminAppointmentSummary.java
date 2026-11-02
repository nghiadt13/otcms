package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Admin list projection; patient contact data is only returned from the admin endpoint. */
public record AdminAppointmentSummary(
        UUID id,
        UUID patientProfileId,
        String patientName,
        String patientPhone,
        UUID serviceId,
        String serviceName,
        BigDecimal servicePrice,
        UUID doctorId,
        String doctorDisplayName,
        String status,
        Instant startTime) { }
