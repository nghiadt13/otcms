package fu.fa26.sep490.g58.otcms_be.appointment.service;

import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;

public interface CreateFollowUpAppointmentService {
    CreatedAppointment create(CreateFollowUpAppointmentCommand command);
}
