package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentChange;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentChangeRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilityQuery;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilitySlot;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentChangeAvailabilityService;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetAppointmentChangeAvailabilityServiceImpl implements GetAppointmentChangeAvailabilityService {
    private final AppointmentChangeRepository appointments;
    private final AuthorizePatientProfileService patientAuthorization;
    private final ServiceDoctorAssignmentQueryService assignments;
    private final StaffAvailabilityProviderQueryService providers;
    private final GenerateAvailableSlotsService slotEngine;
    private final AppointmentBookingProperties properties;
    private final Clock clock;

    public GetAppointmentChangeAvailabilityServiceImpl(AppointmentChangeRepository appointments,
            AuthorizePatientProfileService patientAuthorization,
            ServiceDoctorAssignmentQueryService assignments, StaffAvailabilityProviderQueryService providers,
            GenerateAvailableSlotsService slotEngine, AppointmentBookingProperties properties, Clock clock) {
        this.appointments = appointments;
        this.patientAuthorization = patientAuthorization;
        this.assignments = assignments;
        this.providers = providers;
        this.slotEngine = slotEngine;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentAvailabilitySlot> findRescheduleSlots(UUID appointmentId, UUID accountId,
            AppointmentAvailabilityQuery.Audience actor, LocalDate fromDate, LocalDate toDate) {
        var appointment = findContext(appointmentId);
        if (actor == AppointmentAvailabilityQuery.Audience.PUBLIC) {
            try {
                patientAuthorization.authorize(accountId, appointment.patientProfileId());
            } catch (fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException denied) {
                throw new AppointmentAvailabilityException("APPOINTMENT_NOT_FOUND");
            }
        } else if (actor != AppointmentAvailabilityQuery.Audience.STAFF) {
            throw new AppointmentAvailabilityException("ACCESS_DENIED");
        }
        requireConfirmed(appointment);
        Instant now = Instant.now(clock);
        if (actor == AppointmentAvailabilityQuery.Audience.PUBLIC
                && appointment.startTime().isBefore(now.plus(properties.patientChangeDeadlineMinutes(), ChronoUnit.MINUTES))) {
            throw new AppointmentAvailabilityException("APPOINTMENT_CHANGE_TOO_LATE");
        }
        validateWindow(fromDate, toDate);
        if (!assignments.findActiveDoctorProfileIds(appointment.serviceId()).contains(appointment.doctorProfileId())) {
            throw new AppointmentAvailabilityException("DOCTOR_NOT_ELIGIBLE");
        }
        Instant earliest = actor == AppointmentAvailabilityQuery.Audience.PUBLIC
                ? now.plus(properties.onlineMinimumNoticeMinutes(), ChronoUnit.MINUTES) : now;
        return generate(appointment, Set.of(appointment.doctorProfileId()), fromDate, toDate)
                .stream().filter(slot -> !slot.startTime().isBefore(earliest)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentAvailabilitySlot> findReassignmentSlots(UUID appointmentId, LocalDate fromDate,
            LocalDate toDate) {
        var appointment = findContext(appointmentId);
        requireConfirmed(appointment);
        if (appointment.startTime().isBefore(Instant.now(clock))) {
            throw new AppointmentAvailabilityException("BOOKING_TIME_IN_PAST");
        }
        validateWindow(fromDate, toDate);
        Set<UUID> assigned = Set.copyOf(assignments.findActiveDoctorProfileIds(appointment.serviceId()));
        Set<UUID> replacementDoctors = providers.findAcceptingDoctorsInActiveDirectory().stream()
                .map(StaffAvailabilityProviderQueryService.AvailabilityProvider::doctorProfileId)
                .filter(id -> !id.equals(appointment.doctorProfileId()) && assigned.contains(id))
                .collect(Collectors.toSet());
        if (replacementDoctors.isEmpty()) return List.of();
        return generate(appointment, replacementDoctors, fromDate, toDate).stream()
                .filter(slot -> slot.startTime().equals(appointment.startTime())).toList();
    }

    private AppointmentChange findContext(UUID appointmentId) {
        if (appointmentId == null) throw new AppointmentAvailabilityException("VALIDATION_FAILED");
        return appointments.findChangeContext(appointmentId)
                .orElseThrow(() -> new AppointmentAvailabilityException("APPOINTMENT_NOT_FOUND"));
    }

    private void requireConfirmed(AppointmentChange appointment) {
        if (!"CONFIRMED".equals(appointment.status())) {
            throw new AppointmentAvailabilityException("APPOINTMENT_NOT_CHANGEABLE");
        }
    }

    private List<AppointmentAvailabilitySlot> generate(AppointmentChange appointment,
            Set<UUID> doctors, LocalDate from, LocalDate to) {
        List<AvailableSlot> slots = slotEngine.generate(new SlotAvailabilityRequest(doctors, from, to,
                SlotServiceContext.snapshot(appointment.serviceId(), appointment.durationMinutes(),
                        appointment.bufferMinutes(), appointment.id(), appointment.patientProfileId())));
        var names = providers.findAcceptingDoctorsInActiveDirectory().stream()
                .collect(Collectors.toMap(StaffAvailabilityProviderQueryService.AvailabilityProvider::doctorProfileId,
                        StaffAvailabilityProviderQueryService.AvailabilityProvider::displayName, (a, b) -> a));
        String currentDoctorName = names.get(appointment.doctorProfileId());
        if (currentDoctorName == null && doctors.contains(appointment.doctorProfileId())) {
            currentDoctorName = providers.findDoctorDisplayName(appointment.doctorProfileId())
                    .orElseThrow(() -> new AppointmentAvailabilityException("APPOINTMENT_NOT_FOUND"));
        }
        String fallbackName = currentDoctorName;
        return slots.stream().filter(slot -> doctors.contains(slot.doctorProfileId()))
                .map(slot -> new AppointmentAvailabilitySlot(appointment.serviceId(), slot.doctorProfileId(),
                        names.getOrDefault(slot.doctorProfileId(), fallbackName),
                        slot.startTime(), slot.endTime(), slot.occupiedUntil()))
                .toList();
    }

    private void validateWindow(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) + 1 > (long) properties.maxHorizonDays() + 1) {
            throw new AppointmentAvailabilityException("VALIDATION_FAILED");
        }
        LocalDate today = LocalDate.now(clock.withZone(properties.clinicTimeZone()));
        if (from.isBefore(today) || to.isAfter(today.plusDays(properties.maxHorizonDays()))) {
            throw new AppointmentAvailabilityException("BOOKING_WINDOW_VIOLATION");
        }
    }
}
