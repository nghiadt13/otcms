package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckedInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CheckInAppointmentServiceImpl implements CheckInAppointmentService {
    private final CheckInAppointmentTransaction transaction;

    public CheckInAppointmentServiceImpl(CheckInAppointmentTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public CheckedInAppointment checkIn(UUID appointmentId, CheckInAppointmentCommand command) {
        try {
            return transaction.checkIn(appointmentId, command);
        } catch (DataAccessException exception) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }
}
