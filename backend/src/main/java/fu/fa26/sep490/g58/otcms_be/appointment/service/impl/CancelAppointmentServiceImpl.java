package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CancellableAppointment;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentCancellationRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService;
import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

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
    private final NotificationPublisher notifications;
    private final StaffAvailabilityProviderQueryService doctors;
    private final ReleaseAppointmentPaymentHoldService releaseHold;
    /** Null in plain unit tests (no transaction manager): the body then simply runs without a transaction. */
    private final TransactionTemplate transaction;

    public CancelAppointmentServiceImpl(CoordinationGuardService guard, AppointmentCancellationRepository appointments,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties properties, Clock clock,
            NotificationPublisher notifications, StaffAvailabilityProviderQueryService doctors) {
        this(guard, appointments, patientAuthorization, properties, clock, null, null, notifications, doctors, null, null);
    }

    @Autowired
    public CancelAppointmentServiceImpl(CoordinationGuardService guard, AppointmentCancellationRepository appointments,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties properties, Clock clock,
            AppointmentLifecycleRepository lifecycle, VisitScheduledCareService visits,
            NotificationPublisher notifications, StaffAvailabilityProviderQueryService doctors,
            ReleaseAppointmentPaymentHoldService releaseHold, PlatformTransactionManager transactionManager) {
        this.transaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.releaseHold = releaseHold;
        this.guard = guard;
        this.appointments = appointments;
        this.patientAuthorization = patientAuthorization;
        this.properties = properties;
        this.clock = clock;
        this.lifecycle = lifecycle;
        this.visits = visits;
        this.notifications = notifications;
        this.doctors = doctors;
    }

    /**
     * Deliberately not @Transactional. A payment hold is released OUTSIDE any transaction and BEFORE the coordination
     * guard: billing may call the gateway and may confirm a payment (its own committed transactions), and a
     * "paid in the meantime" answer must not roll that confirmation back. Every other status runs in one transaction.
     */
    @Override
    public CreatedAppointment cancel(UUID appointmentId, CancelAppointmentCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null || command.actor() == null
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128
                || (command.reason() != null && (command.reason().isBlank() || command.reason().trim().length() > 1000))
                || (command.actor() == CancelAppointmentCommand.Actor.RECEPTIONIST
                    && (command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 1000))) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        if (releaseHold != null) {
            var peek = appointments.findStatusAndPatient(appointmentId);
            if (!peek.isEmpty() && "AWAITING_PAYMENT".equals(peek.getFirst()[0])) {
                return cancelPaymentHold(appointmentId, AppointmentRowMapper.uuid(peek.getFirst()[1]), command);
            }
        }
        return inTransaction(() -> cancelInTransaction(appointmentId, command));
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction == null ? work.get() : transaction.execute(status -> work.get());
    }

    private CreatedAppointment cancelInTransaction(UUID appointmentId, CancelAppointmentCommand command) {
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
                var cancelled = lifecycle.transition(checkedIn, command.actorAccountId(), command.actor().name(),
                        "CANCELLED", command.reason().trim(), now, command.correlationId());
                publishCancellation(current, command);
                return cancelled;
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
        var cancelled = appointments.cancel(current, command.actorAccountId(), command.actor().name(),
                command.reason() == null ? null : command.reason().trim(), now, command.correlationId());
        publishCancellation(current, command);
        return cancelled;
    }
    /**
     * Cancelling a payment hold (AWAITING_PAYMENT): only the booking patient may, no change deadline applies (nothing
     * paid to protect), billing settles first (a paid invoice confirms the booking instead), then the slot is freed as
     * PAYMENT_EXPIRED - never CANCELLED. Runs without a transaction and without the guard: no appointment row is
     * locked before billing settled, and when billing found the payment complete the confirmation is already
     * committed by the time the 409 below is thrown.
     */
    private CreatedAppointment cancelPaymentHold(UUID appointmentId, UUID patientProfileId,
            CancelAppointmentCommand command) {
        if (command.actor() != CancelAppointmentCommand.Actor.PATIENT) {
            // Staff never see a hold.
            throw new AppointmentBookingException("APPOINTMENT_NOT_FOUND");
        }
        try {
            patientAuthorization.authorize(command.actorAccountId(), patientProfileId);
        } catch (PatientAuthorizationDeniedException denied) {
            throw new AppointmentBookingException("APPOINTMENT_NOT_FOUND", denied);
        }
        String reason = command.reason() == null ? "Bệnh nhân hủy giữ chỗ thanh toán" : command.reason().trim();
        releaseHold.release(appointmentId, true,
                ReleaseAppointmentPaymentHoldService.Actor.patient(command.actorAccountId(), reason, command.correlationId()));
        var rows = appointments.findCancelledResult(appointmentId);
        if (rows.isEmpty()) throw new AppointmentBookingException("APPOINTMENT_NOT_FOUND");
        var result = AppointmentRowMapper.createdAppointment(rows.getFirst());
        // Paid in the meantime (now CONFIRMED) or otherwise not released: the cancel did not happen.
        if (!"PAYMENT_EXPIRED".equals(result.status())) throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        return result;
    }

    private void publishCancellation(CancellableAppointment appointment, CancelAppointmentCommand command) {
        if (command.actor() == CancelAppointmentCommand.Actor.RECEPTIONIST)
            notifications.publishToPatient(appointment.patientProfileId(), NotificationMessages.appointmentCancelled(
                    appointment.id(), appointment.startTime(), doctors.findDoctorDisplayName(appointment.doctorProfileId()).orElse("")));
    }
}
