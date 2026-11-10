package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentJpaRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService.Actor;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentPaymentHoldRepository;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** @ears REQ-BOOK-003 REQ-BOOK-005 REQ-BOOK-006 REQ-BOOK-010–018 */
@Service
public class CreateAppointmentServiceImpl implements CreateAppointmentService {
    private static final String REPLACED_HOLD_REASON = "Đặt lịch mới thay thế giữ chỗ cũ";
    private final CoordinationGuardService coordinationGuard;
    private final PatientProfileDirectory currentPatientProfile;
    private final AuthorizePatientProfileService patientAuthorization;
    private final ClinicServiceBookingQueryService services;
    private final StaffAvailabilityProviderQueryService providers;
    private final ServiceDoctorAssignmentQueryService assignments;
    private final GenerateAvailableSlotsService slotEngine;
    private final AppointmentJpaRepository appointments;
    private final AppointmentBookingProperties properties;
    private final Clock clock;
    private final NotificationPublisher notifications;
    private final AppointmentPaymentHoldRepository holds;
    private final ReleaseAppointmentPaymentHoldService releaseHolds;
    /** Null in plain unit tests (no transaction manager): the body then simply runs without a transaction. */
    private final TransactionTemplate transaction;

    public CreateAppointmentServiceImpl(CoordinationGuardService coordinationGuard,
            PatientProfileDirectory currentPatientProfile, AuthorizePatientProfileService patientAuthorization,
            ClinicServiceBookingQueryService services, StaffAvailabilityProviderQueryService providers,
            ServiceDoctorAssignmentQueryService assignments, GenerateAvailableSlotsService slotEngine,
            AppointmentJpaRepository appointments, AppointmentBookingProperties properties, Clock clock,
            NotificationPublisher notifications, AppointmentPaymentHoldRepository holds,
            ReleaseAppointmentPaymentHoldService releaseHolds) {
        this(coordinationGuard, currentPatientProfile, patientAuthorization, services, providers, assignments,
                slotEngine, appointments, properties, clock, notifications, holds, releaseHolds, null);
    }

    @Autowired
    public CreateAppointmentServiceImpl(CoordinationGuardService coordinationGuard,
            PatientProfileDirectory currentPatientProfile, AuthorizePatientProfileService patientAuthorization,
            ClinicServiceBookingQueryService services, StaffAvailabilityProviderQueryService providers,
            ServiceDoctorAssignmentQueryService assignments, GenerateAvailableSlotsService slotEngine,
            AppointmentJpaRepository appointments, AppointmentBookingProperties properties, Clock clock,
            NotificationPublisher notifications, AppointmentPaymentHoldRepository holds,
            ReleaseAppointmentPaymentHoldService releaseHolds, PlatformTransactionManager transactionManager) {
        this.transaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.holds = holds;
        this.releaseHolds = releaseHolds;
        this.coordinationGuard = coordinationGuard;
        this.currentPatientProfile = currentPatientProfile;
        this.patientAuthorization = patientAuthorization;
        this.services = services;
        this.providers = providers;
        this.assignments = assignments;
        this.slotEngine = slotEngine;
        this.appointments = appointments;
        this.properties = properties;
        this.clock = clock;
        this.notifications = notifications;
    }

    /**
     * Not @Transactional: the booking patient's own live holds are released first, outside any transaction and
     * before the coordination guard, because billing may call the gateway (and may confirm a payment in its own
     * committed transaction). The booking itself then runs in one transaction.
     */
    @Override
    public CreatedAppointment create(CreateAppointmentCommand command) {
        validate(command);
        releaseOwnHolds(command);
        return inTransaction(() -> createInTransaction(command));
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction == null ? work.get() : transaction.execute(status -> work.get());
    }

    /** A patient account keeps at most one hold: a new booking replaces the older ones (settled with force). */
    private void releaseOwnHolds(CreateAppointmentCommand command) {
        if (command.actor() != CreateAppointmentCommand.Actor.PATIENT || holds == null || releaseHolds == null) return;
        for (UUID id : holds.findHoldIdsByCreator(command.actorAccountId())) {
            releaseHolds.release(id, true,
                    Actor.patient(command.actorAccountId(), REPLACED_HOLD_REASON, command.correlationId()));
        }
    }

    private CreatedAppointment createInTransaction(CreateAppointmentCommand command) {
        coordinationGuard.acquire();
        Instant now = Instant.now(clock);
        UUID patientProfileId = resolvePatient(command);
        LocalDateTime clinicStart = LocalDateTime.ofInstant(command.startTime().toInstant(), properties.clinicTimeZone());
        Instant start = command.startTime().toInstant();
        if (start.isBefore(now)) throw new AppointmentBookingException("BOOKING_TIME_IN_PAST");
        if (clinicStart.getSecond() != 0 || clinicStart.getNano() != 0
                || clinicStart.getMinute() % properties.slotIntervalMinutes() != 0) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        LocalDate startDate = clinicStart.toLocalDate();
        LocalDate clinicToday = LocalDate.ofInstant(now, properties.clinicTimeZone());
        if (startDate.isBefore(clinicToday) || startDate.isAfter(clinicToday.plusDays(properties.maxHorizonDays()))) {
            throw new AppointmentBookingException("BOOKING_WINDOW_VIOLATION");
        }

        ClinicServiceBookingQueryService.BookingAudience audience = command.actor() == CreateAppointmentCommand.Actor.PATIENT
                ? ClinicServiceBookingQueryService.BookingAudience.PUBLIC_PATIENT : ClinicServiceBookingQueryService.BookingAudience.RECEPTIONIST;
        ClinicServiceBookingQueryService.BookableService service;
        try {
            service = services.findActiveService(command.serviceId(), audience);
        } catch (RuntimeException exception) {
            if ("SERVICE_NOT_BOOKABLE".equals(exception.getMessage())) {
                throw new AppointmentBookingException("SERVICE_NOT_BOOKABLE", exception);
            }
            throw exception;
        }
        int durationMinutes = wholeMinutes(service.consultationDuration(), false);
        int bufferMinutes = wholeMinutes(service.bufferDuration(), true);

        boolean eligibleDoctor = providers.findAcceptingDoctorsInActiveDirectory().stream()
                .filter(provider -> provider.doctorProfileId().equals(command.doctorProfileId()))
                .anyMatch(provider -> command.actor() != CreateAppointmentCommand.Actor.PATIENT
                        || provider.publiclyVisible());
        if (!eligibleDoctor || !assignments.findActiveDoctorProfileIds(service.serviceId())
                .contains(command.doctorProfileId())) {
            throw new AppointmentBookingException("DOCTOR_NOT_ELIGIBLE");
        }

        releaseLapsedHolds(command, start, start.plus(Duration.ofMinutes(durationMinutes + (long) bufferMinutes)), now);
        var candidateRequest = new SlotAvailabilityRequest(Set.of(command.doctorProfileId()), startDate, startDate,
                SlotServiceContext.current(service.serviceId(), service.consultationDuration(), service.bufferDuration()));
        var availableSlot = slotEngine.generate(candidateRequest).stream()
                .filter(slot -> slot.startTime().equals(start))
                .findFirst()
                .orElseThrow(() -> new AppointmentBookingException("SLOT_UNAVAILABLE"));

        // Computed after the releases above (they may take time) so the patient gets the full hold window.
        Instant holdNow = Instant.now(clock);
        Instant paymentDeadline = paymentDeadline(command, service.serviceId(), start, holdNow);

        AppointmentBooking booking = new AppointmentBooking(UUID.randomUUID(), patientProfileId, service.serviceId(),
                command.doctorProfileId(), command.actor() == CreateAppointmentCommand.Actor.PATIENT
                        ? AppointmentBooking.BookingSource.PATIENT_ONLINE
                        : AppointmentBooking.BookingSource.RECEPTIONIST_ASSISTED,
                command.purpose(), availableSlot.startTime(), availableSlot.endTime(), durationMinutes, bufferMinutes,
                command.actorAccountId(), command.actor() == CreateAppointmentCommand.Actor.PATIENT
                        ? AppointmentBooking.ActorType.PATIENT : AppointmentBooking.ActorType.RECEPTIONIST,
                holdNow, command.correlationId(), null, null, null, null, paymentDeadline);
        var created = appointments.save(booking);
        // A held slot is not a booking yet: the notification is sent when payment confirms it.
        if (paymentDeadline == null) {
            notifications.publishToPatient(patientProfileId, NotificationMessages.appointmentBooked(booking.id(),
                    booking.startTime(), providers.findDoctorDisplayName(command.doctorProfileId()).orElse("")));
        }
        return created;
    }

    /**
     * Only an online patient booking of a service with a fee holds the slot until payment. Receptionist bookings and
     * free services are confirmed at once. The hold needs the full hold window before the start so the patient can
     * actually pay.
     */
    private Instant paymentDeadline(CreateAppointmentCommand command, UUID serviceId, Instant start, Instant now) {
        if (command.actor() != CreateAppointmentCommand.Actor.PATIENT) return null;
        BigDecimal price = services.findServiceLabel(serviceId)
                .map(ClinicServiceBookingQueryService.ServiceLabel::price).orElse(null);
        if (price == null || price.signum() <= 0) return null;
        Instant deadline = now.plus(Duration.ofMinutes(properties.paymentHoldMinutes()));
        if (start.isBefore(deadline)) throw new AppointmentBookingException("BOOKING_TOO_SOON");
        return deadline;
    }

    /**
     * Lazy release (same transaction as the insert): the DB EXCLUDE constraint has no clock, so lapsed holds of other
     * accounts that overlap the new slot must be released first (settled without force: a live QR keeps the hold).
     */
    private void releaseLapsedHolds(CreateAppointmentCommand command, Instant start, Instant occupiedUntil, Instant now) {
        List<UUID> lapsed = holds.findLapsedHoldIdsOverlapping(command.doctorProfileId(), start, occupiedUntil, now);
        for (UUID id : lapsed) {
            releaseHolds.release(id, false, Actor.system(command.correlationId()));
        }
    }

    private UUID resolvePatient(CreateAppointmentCommand command) {
        if (command.actor() == CreateAppointmentCommand.Actor.RECEPTIONIST) {
            if (command.patientProfileId() == null) throw new AppointmentBookingException("VALIDATION_FAILED");
            return command.patientProfileId();
        }
        UUID profileId = command.patientProfileId() == null
                ? currentPatientProfile.findPatientProfile(command.actorAccountId())
                        .orElseThrow(() -> new AppointmentBookingException("PATIENT_PROFILE_NOT_FOUND"))
                : command.patientProfileId();
        try {
            return patientAuthorization.authorize(command.actorAccountId(), profileId).patientProfileId();
        } catch (PatientAuthorizationDeniedException exception) {
            throw new AppointmentBookingException("PATIENT_PROFILE_NOT_FOUND", exception);
        }
    }

    private int wholeMinutes(Duration duration, boolean allowZero) {
        if (duration == null || duration.isNegative() || (!allowZero && duration.isZero())
                || duration.getNano() != 0 || duration.getSeconds() % 60 != 0) {
            throw new AppointmentBookingException("SERVICE_NOT_BOOKABLE");
        }
        try {
            int minutes = Math.toIntExact(duration.toMinutes());
            return minutes;
        } catch (ArithmeticException exception) {
            throw new AppointmentBookingException("SERVICE_NOT_BOOKABLE", exception);
        }
    }

    private void validate(CreateAppointmentCommand command) {
        if (command == null || command.actorAccountId() == null || command.actor() == null
                || command.serviceId() == null || command.doctorProfileId() == null || command.startTime() == null
                || command.purpose() == null || command.purpose().isBlank() || command.purpose().trim().length() > 1000
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
    }
}
