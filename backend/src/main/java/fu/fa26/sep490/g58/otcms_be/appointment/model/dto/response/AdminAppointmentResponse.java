package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AdminAppointmentSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminAppointmentResponse(
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
        Instant startTime,
        Boolean examFeePaid) {
    public static AdminAppointmentResponse from(AdminAppointmentSummary summary) {
        return new AdminAppointmentResponse(summary.id(), summary.patientProfileId(), summary.patientName(),
                summary.patientPhone(), summary.serviceId(), summary.serviceName(), summary.servicePrice(),
                summary.doctorId(), summary.doctorDisplayName(), summary.status(), summary.startTime(), null);
    }
    public AdminAppointmentResponse withExamFeePaid(Boolean paid) {
        return new AdminAppointmentResponse(id, patientProfileId, patientName, patientPhone, serviceId, serviceName,
                servicePrice, doctorId, doctorDisplayName, status, startTime, paid);
    }
}
