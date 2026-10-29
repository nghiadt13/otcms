package fu.fa26.sep490.g58.otcms_be.appointment.mapper;

import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.AppointmentResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.CheckInResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSummary;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckedInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;

public final class AppointmentMapper {
    private AppointmentMapper() { }

    public static AppointmentResponse toResponse(CreatedAppointment appointment) {
        // Write endpoints do not resolve names; clients read them from GET /api/v1/appointments[/{id}].
        return new AppointmentResponse(appointment.id(), appointment.patientProfileId(), appointment.serviceId(),
                null, null, appointment.doctorId(), null, appointment.status(), appointment.bookingSource(),
                appointment.purpose(),
                appointment.startTime(), appointment.endTime(), appointment.durationMinutes(),
                appointment.bufferMinutes(), appointment.occupiedUntil(), appointment.visitId(),
                appointment.sourceVisitId(), appointment.createdAt(), appointment.updatedAt());
    }

    public static AppointmentResponse toResponse(AppointmentSummary appointment) {
        return new AppointmentResponse(appointment.id(), appointment.patientProfileId(), appointment.serviceId(),
                appointment.serviceName(), appointment.servicePrice(), appointment.doctorId(),
                appointment.doctorDisplayName(), appointment.status(), appointment.bookingSource(), appointment.purpose(),
                appointment.startTime(), appointment.endTime(), appointment.durationMinutes(),
                appointment.bufferMinutes(), appointment.occupiedUntil(), appointment.visitId(),
                appointment.sourceVisitId(), appointment.createdAt(), appointment.updatedAt());
    }

    public static CheckInResponse toResponse(CheckedInAppointment appointment) {
        return new CheckInResponse(appointment.appointmentId(), appointment.visitId(), appointment.checkedInAt());
    }
}
