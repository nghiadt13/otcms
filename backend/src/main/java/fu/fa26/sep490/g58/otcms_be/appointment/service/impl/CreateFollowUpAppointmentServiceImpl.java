package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentJpaRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateFollowUpAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateFollowUpAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitWorkflowService;
import org.springframework.dao.DataAccessException;
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

@Service
public class CreateFollowUpAppointmentServiceImpl implements CreateFollowUpAppointmentService {
    private final CoordinationGuardService guard;
    private final DoctorProfileIdentityQueryService doctors;
    private final VisitWorkflowService visits;
    private final ClinicServiceBookingQueryService services;
    private final StaffAvailabilityProviderQueryService providers;
    private final ServiceDoctorAssignmentQueryService assignments;
    private final GenerateAvailableSlotsService slots;
    private final AppointmentJpaRepository appointments;
    private final AppointmentBookingProperties properties;
    private final Clock clock;

    public CreateFollowUpAppointmentServiceImpl(CoordinationGuardService guard, DoctorProfileIdentityQueryService doctors,
            VisitWorkflowService visits, ClinicServiceBookingQueryService services, StaffAvailabilityProviderQueryService providers,
            ServiceDoctorAssignmentQueryService assignments, GenerateAvailableSlotsService slots,
            AppointmentJpaRepository appointments, AppointmentBookingProperties properties, Clock clock) {
        this.guard = guard; this.doctors = doctors; this.visits = visits; this.services = services;
        this.providers = providers; this.assignments = assignments; this.slots = slots;
        this.appointments = appointments; this.properties = properties; this.clock = clock;
    }

    @Override
    @Transactional
    public CreatedAppointment create(CreateFollowUpAppointmentCommand command) {
        validate(command);
        acquireGuard();
        Instant now = Instant.now(clock);
        UUID authenticatedDoctor;
        try {
            authenticatedDoctor = doctors.findDoctorProfileId(command.actorAccountId())
                    .orElseThrow(() -> new AppointmentBookingException("ACCESS_DENIED"));
        } catch (DataAccessException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        } catch (AppointmentBookingException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }

        VisitWorkflowService.FollowUpEvidence evidence;
        try {
            evidence = visits.requireFollowUpEvidence(command.sourceVisitId(), authenticatedDoctor);
        } catch (VisitWorkflowException failure) {
            if ("SOURCE_VISIT_NOT_FOUND".equals(failure.code())) throw new AppointmentBookingException(failure.code(), failure);
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        } catch (RuntimeException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
        if (evidence == null || !command.sourceVisitId().equals(evidence.visitId())
                || !authenticatedDoctor.equals(evidence.authenticatedDoctorProfileId())) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE");
        }
        if (evidence.patientProfileId() == null) throw new AppointmentBookingException("SOURCE_VISIT_NOT_FOUND");
        if (!evidence.doctorAssignedToCare()) {
            throw new AppointmentBookingException("SOURCE_VISIT_NOT_FOUND");
        }
        if (!evidence.examinationCompleted() || !evidence.followUpIndicated()) {
            throw new AppointmentBookingException("FOLLOW_UP_NOT_ALLOWED");
        }

        Instant start = command.startTime().toInstant();
        LocalDateTime clinicStart = LocalDateTime.ofInstant(start, properties.clinicTimeZone());
        if (start.isBefore(now)) throw new AppointmentBookingException("BOOKING_TIME_IN_PAST");
        if (clinicStart.getSecond() != 0 || clinicStart.getNano() != 0
                || clinicStart.getMinute() % properties.slotIntervalMinutes() != 0) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        LocalDate startDate = clinicStart.toLocalDate();
        LocalDate today = LocalDate.ofInstant(now, properties.clinicTimeZone());
        if (startDate.isBefore(today) || startDate.isAfter(today.plusDays(properties.maxHorizonDays()))) {
            throw new AppointmentBookingException("BOOKING_WINDOW_VIOLATION");
        }
        ClinicServiceBookingQueryService.BookableService service;
        try {
            service = services.findActiveService(command.serviceId(), ClinicServiceBookingQueryService.BookingAudience.DOCTOR_FOLLOW_UP);
        } catch (RuntimeException failure) {
            if ("SERVICE_NOT_BOOKABLE".equals(failure.getMessage())) {
                throw new AppointmentBookingException("SERVICE_NOT_BOOKABLE", failure);
            }
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
        int duration = minutes(service.consultationDuration(), false);
        int buffer = minutes(service.bufferDuration(), true);
        AvailableSlot slot;
        try {
            boolean eligible = providers.findAcceptingDoctorsInActiveDirectory().stream()
                    .anyMatch(provider -> command.doctorId().equals(provider.doctorProfileId()));
            if (!eligible || !assignments.findActiveDoctorProfileIds(service.serviceId()).contains(command.doctorId())) {
                throw new AppointmentBookingException("DOCTOR_NOT_ELIGIBLE");
            }
            slot = slots.generate(new SlotAvailabilityRequest(Set.of(command.doctorId()), startDate, startDate,
                    SlotServiceContext.current(service.serviceId(), service.consultationDuration(), service.bufferDuration())))
                    .stream().filter(candidate -> candidate.startTime().equals(start)).findFirst()
                    .orElseThrow(() -> new AppointmentBookingException("SLOT_UNAVAILABLE"));
        } catch (DataAccessException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        } catch (AppointmentBookingException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }

        AppointmentBooking booking = new AppointmentBooking(UUID.randomUUID(), evidence.patientProfileId(),
                service.serviceId(), command.doctorId(), AppointmentBooking.BookingSource.DOCTOR_FOLLOW_UP,
                command.purpose(), slot.startTime(), slot.endTime(), duration, buffer, command.actorAccountId(),
                AppointmentBooking.ActorType.DOCTOR, now, command.correlationId(), command.sourceVisitId(),
                evidence.originatingAppointmentId(), authenticatedDoctor, command.reason());
        return appointments.saveFollowUp(booking);
    }

    private void acquireGuard() {
        try { guard.acquire(); }
        catch (CoordinationGuardUnavailableException | DataAccessException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
    }

    private int minutes(Duration value, boolean allowZero) {
        if (value == null || value.isNegative() || (!allowZero && value.isZero())
                || value.getNano() != 0 || value.getSeconds() % 60 != 0) {
            throw new AppointmentBookingException("SERVICE_NOT_BOOKABLE");
        }
        try { return Math.toIntExact(value.toMinutes()); }
        catch (ArithmeticException failure) { throw new AppointmentBookingException("SERVICE_NOT_BOOKABLE", failure); }
    }

    private void validate(CreateFollowUpAppointmentCommand command) {
        if (command == null || command.actorAccountId() == null || command.sourceVisitId() == null
                || command.serviceId() == null || command.doctorId() == null || command.startTime() == null
                || command.purpose() == null || command.purpose().isBlank() || command.purpose().trim().length() > 1000
                || command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 1000
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
    }
}
