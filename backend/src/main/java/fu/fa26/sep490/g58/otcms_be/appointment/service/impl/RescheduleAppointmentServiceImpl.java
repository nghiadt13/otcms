package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentAuditEntries;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentRescheduleRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.RescheduleAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.RescheduleAppointmentService;

import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class RescheduleAppointmentServiceImpl implements RescheduleAppointmentService {
    private final AuditLogWriter auditLogWriter;
    private final CoordinationGuardService guard;
    private final AppointmentRescheduleRepository appointments;
    private final AuthorizePatientProfileService patientAuthorization;
    private final GenerateAvailableSlotsService slotEngine;
    private final AppointmentBookingProperties properties;
    private final Clock clock;
    private final NotificationPublisher notifications;
    private final StaffAvailabilityProviderQueryService doctors;

    public RescheduleAppointmentServiceImpl(CoordinationGuardService guard, AppointmentRescheduleRepository appointments,
            AuthorizePatientProfileService patientAuthorization, GenerateAvailableSlotsService slotEngine,
            AppointmentBookingProperties properties, Clock clock,
            NotificationPublisher notifications, StaffAvailabilityProviderQueryService doctors, AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
        this.guard = guard;
        this.appointments = appointments;
        this.patientAuthorization = patientAuthorization;
        this.slotEngine = slotEngine;
        this.properties = properties;
        this.clock = clock;
        this.notifications = notifications;
        this.doctors = doctors;
    }

    @Override
    @Transactional
    public CreatedAppointment reschedule(UUID appointmentId, RescheduleAppointmentCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null || command.actor() == null
                || command.startTime() == null || command.correlationId() == null
                || command.correlationId().isBlank() || command.correlationId().length() > 128
                || (command.actor() == RescheduleAppointmentCommand.Actor.RECEPTIONIST
                    && (command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 1000))
                || (command.reason() != null
                    && (command.reason().isBlank() || command.reason().trim().length() > 1000))) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        guard.acquire();
        var current = appointments.findForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentBookingException("APPOINTMENT_NOT_FOUND"));
        if (command.actor() == RescheduleAppointmentCommand.Actor.PATIENT) {
            try {
                patientAuthorization.authorize(command.actorAccountId(), current.patientProfileId());
            } catch (PatientAuthorizationDeniedException denied) {
                throw new AppointmentBookingException("APPOINTMENT_NOT_FOUND", denied);
            }
            // B1: a patient cannot move their own booking; the clinic reschedules through the receptionist.
            throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        }
        if (!"CONFIRMED".equals(current.status())) throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        Instant now = Instant.now(clock);
        Instant start = command.startTime().toInstant();
        if (start.isBefore(now)) throw new AppointmentBookingException("BOOKING_TIME_IN_PAST");
        LocalDateTime clinicStart = LocalDateTime.ofInstant(start, properties.clinicTimeZone());
        if (clinicStart.getSecond() != 0 || clinicStart.getNano() != 0
                || clinicStart.getMinute() % properties.slotIntervalMinutes() != 0) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        LocalDate today = LocalDate.ofInstant(now, properties.clinicTimeZone());
        if (clinicStart.toLocalDate().isBefore(today)
                || clinicStart.toLocalDate().isAfter(today.plusDays(properties.maxHorizonDays()))) {
            throw new AppointmentBookingException("BOOKING_WINDOW_VIOLATION");
        }
        LocalDate targetDate = clinicStart.toLocalDate();
        var requested = new SlotAvailabilityRequest(Set.of(current.doctorProfileId()), targetDate, targetDate,
                SlotServiceContext.snapshot(current.serviceId(), current.durationMinutes(), current.bufferMinutes(),
                        current.id(), current.patientProfileId()));
        var slot = slotEngine.generate(requested).stream().filter(candidate -> candidate.startTime().equals(start))
                .findFirst().orElseThrow(() -> new AppointmentBookingException("SLOT_UNAVAILABLE"));
        var changed = appointments.reschedule(current, slot.startTime(), slot.endTime(), command.actorAccountId(),
                command.actor().name(), command.reason() == null ? null : command.reason().trim(), now,
                command.correlationId());
        AppointmentAuditEntries.record(auditLogWriter, AppointmentAuditEntries.changed(current.id(), command.actorAccountId(),
                command.actor().name(), "RESCHEDULED", "CONFIRMED", "CONFIRMED", current.doctorProfileId(),
                current.doctorProfileId(), current.startTime(), slot.startTime(), current.endTime(), slot.endTime(),
                null, null, command.reason() == null ? null : command.reason().trim(), null, now), command.correlationId());
        notifications.publishToPatient(current.patientProfileId(), NotificationMessages.appointmentRescheduled(
                current.id(), slot.startTime(), doctors.findDoctorDisplayName(current.doctorProfileId()).orElse("")));
        return changed;
    }
}
