package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckedInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentService;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CheckInAppointmentServiceImpl implements CheckInAppointmentService {
    private static final Logger logger = LoggerFactory.getLogger(CheckInAppointmentServiceImpl.class);
    private final CheckInAppointmentTransaction transaction;

    public CheckInAppointmentServiceImpl(CheckInAppointmentTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public CheckedInAppointment checkIn(UUID appointmentId, CheckInAppointmentCommand command) {
        try {
            return transaction.checkIn(appointmentId, command);
        } catch (DataAccessException exception) {
            logger.error("Appointment check-in persistence failed for appointment {}", appointmentId, exception);
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }
}
