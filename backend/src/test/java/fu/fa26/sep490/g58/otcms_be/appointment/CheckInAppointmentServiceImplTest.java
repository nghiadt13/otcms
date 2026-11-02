package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.model.*;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.CheckInAppointmentServiceImpl;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.CheckInAppointmentTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckInAppointmentServiceImplTest {
    @Test
    void mapsPersistenceFailureFromTransactionBoundaryToDependencyUnavailable() {
        var transaction = mock(CheckInAppointmentTransaction.class);
        var handler = new CheckInAppointmentServiceImpl(transaction);
        var appointmentId = UUID.randomUUID();
        var command = new CheckInAppointmentCommand(UUID.randomUUID(), UUID.randomUUID(), "key", "correlation");
        when(transaction.checkIn(appointmentId, command))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        var exception = assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command));

        assertEquals("DEPENDENCY_UNAVAILABLE", exception.code());
    }
}
