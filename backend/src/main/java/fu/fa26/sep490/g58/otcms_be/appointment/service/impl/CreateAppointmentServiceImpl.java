package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentJpaRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/** @ears REQ-BOOK-003 REQ-BOOK-005 REQ-BOOK-006 REQ-BOOK-010–018 */
@Service
public class CreateAppointmentServiceImpl implements CreateAppointmentService {
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

    public CreateAppointmentServiceImpl(CoordinationGuardService coordinationGuard,
            PatientProfileDirectory currentPatientProfile, AuthorizePatientProfileService patientAuthorization,
            ClinicServiceBookingQueryService services, StaffAvailabilityProviderQueryService providers,
            ServiceDoctorAssignmentQueryService assignments, GenerateAvailableSlotsService slotEngine,
            AppointmentJpaRepository appointments, AppointmentBookingProperties properties, Clock clock) {
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
    }

    @Override
    @Transactional
    public CreatedAppointment create(CreateAppointmentCommand command) {
        validate(command);
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
        if (command.actor() == CreateAppointmentCommand.Actor.PATIENT
                && start.isBefore(now.plus(properties.onlineMinimumNoticeMinutes(), ChronoUnit.MINUTES))) {
            throw new AppointmentBookingException("BOOKING_NOTICE_TOO_SHORT");
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

        var candidateRequest = new SlotAvailabilityRequest(Set.of(command.doctorProfileId()), startDate, startDate,
                SlotServiceContext.current(service.serviceId(), service.consultationDuration(), service.bufferDuration()));
        var availableSlot = slotEngine.generate(candidateRequest).stream()
                .filter(slot -> slot.startTime().equals(start))
                .findFirst()
                .orElseThrow(() -> new AppointmentBookingException("SLOT_UNAVAILABLE"));

        AppointmentBooking booking = new AppointmentBooking(UUID.randomUUID(), patientProfileId, service.serviceId(),
                command.doctorProfileId(), command.actor() == CreateAppointmentCommand.Actor.PATIENT
                        ? AppointmentBooking.BookingSource.PATIENT_ONLINE
                        : AppointmentBooking.BookingSource.RECEPTIONIST_ASSISTED,
                command.purpose(), availableSlot.startTime(), availableSlot.endTime(), durationMinutes, bufferMinutes,
                command.actorAccountId(), command.actor() == CreateAppointmentCommand.Actor.PATIENT
                        ? AppointmentBooking.ActorType.PATIENT : AppointmentBooking.ActorType.RECEPTIONIST,
                now, command.correlationId());
        return appointments.save(booking);
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
