package fu.fa26.sep490.g58.otcms_be.appointment.service;

import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;

import java.util.UUID;

public interface MarkAppointmentNoShowService {
    CreatedAppointment markNoShow(UUID appointmentId, MarkAppointmentNoShowCommand command);
}
