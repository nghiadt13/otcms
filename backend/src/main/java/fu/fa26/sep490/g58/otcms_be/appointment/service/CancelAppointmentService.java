package fu.fa26.sep490.g58.otcms_be.appointment.service;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;


import java.util.UUID;

public interface CancelAppointmentService {
    CreatedAppointment cancel(UUID appointmentId, CancelAppointmentCommand command);
}
