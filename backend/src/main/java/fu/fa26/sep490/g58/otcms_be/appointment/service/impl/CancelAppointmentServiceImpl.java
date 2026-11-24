package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentAuditEntries;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CancellableAppointment;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentCancellationRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentService;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentInvoicePaidStatusQueryService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService;

import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitScheduledCareService;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentLifecycleRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class CancelAppointmentServiceImpl implements CancelAppointmentService {
    private final AuditLogWriter auditLogWriter;
    private final CoordinationGuardService guard;
    private final AppointmentCancellationRepository appointments;
    private final AuthorizePatientProfileService patientAuthorization;
    private final AppointmentBookingProperties properties;
    private final Clock clock;
    private final NotificationPublisher notifications;
    private final StaffAvailabilityProviderQueryService doctors;
    private final BillingRefundService fees;
    private final AppointmentInvoicePaidStatusQueryService invoicePaidStatus;
    /** Null in plain unit tests (no transaction manager): the body then simply runs without a transaction. */
    private final TransactionTemplate transaction;

    public CancelAppointmentServiceImpl(CoordinationGuardService guard, AppointmentCancellationRepository appointments,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties properties, Clock clock,
            NotificationPublisher notifications, StaffAvailabilityProviderQueryService doctors, BillingRefundService fees, AuditLogWriter auditLogWriter) {
        this(guard, appointments, patientAuthorization, properties, clock, null, null, notifications, doctors, fees, null, auditLogWriter, null);
    }

    public CancelAppointmentServiceImpl(CoordinationGuardService guard, AppointmentCancellationRepository appointments,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties properties, Clock clock,
            NotificationPublisher notifications, StaffAvailabilityProviderQueryService doctors, BillingRefundService fees,
            AuditLogWriter auditLogWriter, AppointmentInvoicePaidStatusQueryService invoicePaidStatus) {
        this(guard, appointments, patientAuthorization, properties, clock, null, null, notifications, doctors, fees, null, auditLogWriter, invoicePaidStatus);
    }

    public CancelAppointmentServiceImpl(CoordinationGuardService guard, AppointmentCancellationRepository appointments,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties properties, Clock clock,
            AppointmentLifecycleRepository lifecycle, VisitScheduledCareService visits,
            NotificationPublisher notifications, StaffAvailabilityProviderQueryService doctors,
            BillingRefundService fees, PlatformTransactionManager transactionManager, AuditLogWriter auditLogWriter) {
        this(guard, appointments, patientAuthorization, properties, clock, lifecycle, visits, notifications, doctors,
                fees, transactionManager, auditLogWriter, null);
    }

    @Autowired
    public CancelAppointmentServiceImpl(CoordinationGuardService guard, AppointmentCancellationRepository appointments,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties properties, Clock clock,
            AppointmentLifecycleRepository lifecycle, VisitScheduledCareService visits,
            NotificationPublisher notifications, StaffAvailabilityProviderQueryService doctors,
            BillingRefundService fees, PlatformTransactionManager transactionManager, AuditLogWriter auditLogWriter,
            AppointmentInvoicePaidStatusQueryService invoicePaidStatus) {
        this.auditLogWriter = auditLogWriter;
        this.transaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.fees = fees;
        this.guard = guard;
        this.appointments = appointments;
        this.patientAuthorization = patientAuthorization;
        this.properties = properties;
        this.clock = clock;
        this.notifications = notifications;
        this.doctors = doctors;
        this.invoicePaidStatus = invoicePaidStatus;
    }

    /** Runs in one transaction: the appointment and its exam-fee item change together (D7: no automatic refunds). */
    @Override
    public CreatedAppointment cancel(UUID appointmentId, CancelAppointmentCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null || command.actor() == null
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128
                || (command.reason() != null && (command.reason().isBlank() || command.reason().trim().length() > 1000))
                || (command.actor() != CancelAppointmentCommand.Actor.PATIENT
                    && (command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 1000))) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
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
        if (command.actor() != CancelAppointmentCommand.Actor.PATIENT && "IN_PROGRESS".equals(current.status())) {
            throw new AppointmentBookingException("VISIT_CANCELLATION_NOT_ALLOWED");
        }
        if ("CHECKED_IN".equals(current.status())) {
            throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        }
        if (!"CONFIRMED".equals(current.status())) throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        if (command.actor() == CancelAppointmentCommand.Actor.PATIENT && isExamFeePaid(appointmentId)) {
            throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        }
        if (command.actor() == CancelAppointmentCommand.Actor.PATIENT
                && current.startTime().isBefore(now.plus(properties.patientChangeDeadlineMinutes(), ChronoUnit.MINUTES))) {
            throw new AppointmentBookingException("APPOINTMENT_CHANGE_TOO_LATE");
        }
        CreatedAppointment cancelled;
        try {
            cancelled = appointments.cancel(current, command.actorAccountId(), command.actor().name(),
                    command.reason() == null ? null : command.reason().trim(), now, command.correlationId());
            AppointmentAuditEntries.record(auditLogWriter, AppointmentAuditEntries.changed(current.id(), command.actorAccountId(), command.actor().name(), "CANCELLED",
                    "CONFIRMED", "CANCELLED", current.doctorProfileId(), current.doctorProfileId(),
                    current.startTime(), current.startTime(), current.endTime(), current.endTime(),
                    current.visitId(), current.sourceVisitId(), command.reason() == null ? null : command.reason().trim(), null, now), command.correlationId());
            settleFee(current, command);
        } catch (DataAccessException failure) {
            // Audit or update failure rolls the whole cancellation back and surfaces as 503, like check-in.
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
        publishCancellation(current, command);
        return cancelled;
    }
    /**
     * The fee follows the appointment: an unpaid fee item is voided; a paid one stays paid (no refund) unless the
     * clinic is at fault (staff set clinicFault: doctor absent, nobody to replace), then it awaits a manual refund.
     */
    private void settleFee(CancellableAppointment appointment, CancelAppointmentCommand command) {
        if (fees == null) return;
        if (command.actor() != CancelAppointmentCommand.Actor.PATIENT && command.clinicFault()) {
            fees.refundAppointmentFee(appointment.id(), command.reason().trim(), appointment.startTime());
        } else {
            fees.voidUnpaidAppointmentFee(appointment.id());
        }
    }

    private boolean isExamFeePaid(UUID appointmentId) {
        return invoicePaidStatus != null
                && Boolean.TRUE.equals(invoicePaidStatus.paidStatus(List.of(appointmentId)).get(appointmentId));
    }

    private void publishCancellation(CancellableAppointment appointment, CancelAppointmentCommand command) {
        if (command.actor() != CancelAppointmentCommand.Actor.PATIENT)
            notifications.publishToPatient(appointment.patientProfileId(), NotificationMessages.appointmentCancelled(
                    appointment.id(), appointment.startTime(), doctors.findDoctorDisplayName(appointment.doctorProfileId()).orElse("")));
    }
}
