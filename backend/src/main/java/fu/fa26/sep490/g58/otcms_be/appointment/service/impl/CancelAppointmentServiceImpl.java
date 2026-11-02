package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentCancellationRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentService;

import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitScheduledCareService;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentLifecycleRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CancelAppointmentServiceImpl implements CancelAppointmentService {
    private static final Logger log = LoggerFactory.getLogger(CancelAppointmentServiceImpl.class);
    private final CoordinationGuardService guard;
    private final AppointmentCancellationRepository appointments;
    private final AuthorizePatientProfileService patientAuthorization;
    private final AppointmentBookingProperties properties;
    private final Clock clock;
    private final AppointmentLifecycleRepository lifecycle;
    private final VisitScheduledCareService visits;

    public CancelAppointmentServiceImpl(CoordinationGuardService guard, AppointmentCancellationRepository appointments,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties properties, Clock clock) {
        this(guard, appointments, patientAuthorization, properties, clock, null, null);
    }

    @Autowired
    public CancelAppointmentServiceImpl(CoordinationGuardService guard, AppointmentCancellationRepository appointments,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties properties, Clock clock,
            AppointmentLifecycleRepository lifecycle, VisitScheduledCareService visits) {
        this.guard = guard;
        this.appointments = appointments;
        this.patientAuthorization = patientAuthorization;
        this.properties = properties;
        this.clock = clock;
        this.lifecycle = lifecycle;
        this.visits = visits;
    }

    @Override
    @Transactional
    public CreatedAppointment cancel(UUID appointmentId, CancelAppointmentCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null || command.actor() == null
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128
                || (command.reason() != null && (command.reason().isBlank() || command.reason().trim().length() > 1000))
                || (command.actor() == CancelAppointmentCommand.Actor.RECEPTIONIST
                    && (command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 1000))) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        try { guard.acquire(); }
        catch (CoordinationGuardUnavailableException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        } catch (DataAccessException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
        var current = appointments.findForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentBookingException("APPOINTMENT_NOT_FOUND"));
        if (command.actor() == CancelAppointmentCommand.Actor.PATIENT) {
            try {
                patientAuthorization.authorize(command.actorAccountId(), current.patientProfileId());
            } catch (PatientAuthorizationDeniedException denied) {
                throw new AppointmentBookingException("APPOINTMENT_NOT_FOUND", denied);
            }
        }
        Instant now = Instant.now(clock);
        if (command.actor() == CancelAppointmentCommand.Actor.RECEPTIONIST
                && "IN_PROGRESS".equals(current.status())) {
            throw new AppointmentBookingException("VISIT_CANCELLATION_NOT_ALLOWED");
        }
        if ("CHECKED_IN".equals(current.status())) {
            if (command.actor() != CancelAppointmentCommand.Actor.RECEPTIONIST) {
                throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
            }
            if (lifecycle == null || visits == null) {
                throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
            }
            if (current.visitId() == null) {
                throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
            }
            try {
                visits.cancelCheckedInVisit(new VisitScheduledCareService.ScheduledCareCommand(appointmentId,
                        current.visitId(), current.patientProfileId(), current.doctorProfileId(), current.endTime(), now,
                        command.actorAccountId(), current.durationMinutes(), command.correlationId()));
            } catch (VisitWorkflowException failure) {
                if ("VISIT_CANCELLATION_NOT_ALLOWED".equals(failure.code())
                        || "VISIT_LINK_CONFLICT".equals(failure.code())) {
                    throw new AppointmentBookingException(failure.code(), failure);
                }
                throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
            } catch (DataAccessException failure) {
                log.error("Checked-in visit cancellation persistence failed for appointment {}", appointmentId, failure);
                throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
            }
            var checkedIn = lifecycle.findForUpdate(appointmentId)
                    .orElseThrow(() -> new AppointmentBookingException("APPOINTMENT_NOT_FOUND"));
            if (!"CHECKED_IN".equals(checkedIn.status())) throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
            try {
                return lifecycle.transition(checkedIn, command.actorAccountId(), command.actor().name(),
                        "CANCELLED", command.reason().trim(), now, command.correlationId());
            } catch (DataAccessException failure) {
                log.error("Checked-in appointment lifecycle persistence failed for appointment {}", appointmentId, failure);
                throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
            }
        }
        if (!"CONFIRMED".equals(current.status())) throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        if (command.actor() == CancelAppointmentCommand.Actor.PATIENT
                && current.startTime().isBefore(now.plus(properties.patientChangeDeadlineMinutes(), ChronoUnit.MINUTES))) {
            throw new AppointmentBookingException("APPOINTMENT_CHANGE_TOO_LATE");
        }
        return appointments.cancel(current, command.actorAccountId(), command.actor().name(),
                command.reason() == null ? null : command.reason().trim(), now, command.correlationId());
    }
}
