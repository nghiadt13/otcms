package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.service.MarkAppointmentNoShowCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.MarkAppointmentNoShowService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MarkAppointmentNoShowServiceImpl implements MarkAppointmentNoShowService {
    private final MarkAppointmentNoShowTransaction transaction;

    public MarkAppointmentNoShowServiceImpl(MarkAppointmentNoShowTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public CreatedAppointment markNoShow(UUID appointmentId, MarkAppointmentNoShowCommand command) {
        try {
            return transaction.markNoShow(appointmentId, command);
        } catch (DataAccessException exception) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }
}
