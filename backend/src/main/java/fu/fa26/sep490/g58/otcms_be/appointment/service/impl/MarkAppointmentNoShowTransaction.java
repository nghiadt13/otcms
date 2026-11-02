package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentNoShowRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.MarkAppointmentNoShowCommand;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.service.RecordedArrivalQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.RecordedArrivalNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class MarkAppointmentNoShowTransaction {
    private final CoordinationGuardService guard;
    private final AppointmentNoShowRepository appointments;
    private final RecordedArrivalQueryService arrivals;
    private final AppointmentBookingProperties properties;
    private final Clock clock;

    public MarkAppointmentNoShowTransaction(CoordinationGuardService guard, AppointmentNoShowRepository appointments,
            RecordedArrivalQueryService arrivals, AppointmentBookingProperties properties, Clock clock) {
        this.guard = guard;
        this.appointments = appointments;
        this.arrivals = arrivals;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CreatedAppointment markNoShow(UUID appointmentId, MarkAppointmentNoShowCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128
                || (command.reason() != null && (command.reason().isBlank()
                    || command.reason().trim().length() > 1000))) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }

        guard.acquire();
        var current = appointments.findForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentBookingException("APPOINTMENT_NOT_FOUND"));
        if (!"CONFIRMED".equals(current.status()) || current.visitId() != null) {
            throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        }

        Instant now = Instant.now(clock);
        Instant windowStart = current.startTime().minus(properties.checkInEarlyMinutes(), ChronoUnit.MINUTES);
        Instant windowEnd = current.startTime().plus(properties.checkInLateGraceMinutes(), ChronoUnit.MINUTES);
        if (!now.isAfter(windowEnd)) {
            throw new AppointmentBookingException("NO_SHOW_NOT_ALLOWED");
        }

        try {
            var arrival = arrivals.requireRecordedArrival(appointmentId, current.patientProfileId(),
                    windowStart, windowEnd);
            if (arrival == null || !appointmentId.equals(arrival.appointmentId())
                    || !current.patientProfileId().equals(arrival.patientProfileId())
                    || arrival.arrivedAt() == null || arrival.arrivedAt().isBefore(windowStart)
                    || arrival.arrivedAt().isAfter(windowEnd)) {
                throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE");
            }
            throw new AppointmentBookingException("NO_SHOW_NOT_ALLOWED");
        } catch (RecordedArrivalNotFoundException absent) {
            // Absence of a matching arrival in the requested inclusive window permits NO_SHOW.
        } catch (AppointmentBookingException expected) {
            throw expected;
        } catch (RuntimeException unavailable) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", unavailable);
        }

        return appointments.markNoShow(current, command.actorAccountId(),
                command.reason() == null ? null : command.reason().trim(), now, command.correlationId());
    }
}
