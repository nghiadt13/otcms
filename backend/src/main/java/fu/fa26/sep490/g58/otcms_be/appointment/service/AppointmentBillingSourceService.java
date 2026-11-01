package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Internal feature contract for billing to resolve a registered appointment service. */
public interface AppointmentBillingSourceService {
    BillingSource requireSource(UUID appointmentId);

    record BillingSource(UUID appointmentId, UUID patientProfileId, UUID serviceId, String status,
            String serviceName, BigDecimal servicePrice) { }
}
