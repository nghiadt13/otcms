package fu.fa26.sep490.g58.otcms_be.appointment.service;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckedInAppointment;


import java.util.UUID;

public interface CheckInAppointmentService {
    CheckedInAppointment checkIn(UUID appointmentId, CheckInAppointmentCommand command);
}
