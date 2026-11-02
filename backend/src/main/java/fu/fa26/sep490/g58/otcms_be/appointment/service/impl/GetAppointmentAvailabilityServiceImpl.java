package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilityQuery;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilitySlot;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentAvailabilityService;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GetAppointmentAvailabilityServiceImpl implements GetAppointmentAvailabilityService {
    private final ClinicServiceBookingQueryService services;
    private final StaffAvailabilityProviderQueryService providers;
    private final ServiceDoctorAssignmentQueryService assignments;
    private final GenerateAvailableSlotsService slotEngine;
    private final AppointmentBookingProperties properties;
    private final Clock clock;

    @Autowired
    public GetAppointmentAvailabilityServiceImpl(ClinicServiceBookingQueryService services,
                                             StaffAvailabilityProviderQueryService providers,
                                             ServiceDoctorAssignmentQueryService assignments,
                                             GenerateAvailableSlotsService slotEngine,
                                             AppointmentBookingProperties properties,
                                             Clock clock) {
        this.services = services;
        this.providers = providers;
        this.assignments = assignments;
        this.slotEngine = slotEngine;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentAvailabilitySlot> find(AppointmentAvailabilityQuery query) {
        validateWindow(query);
        Instant now = Instant.now(clock);
        var bookingAudience = query.audience() == AppointmentAvailabilityQuery.Audience.PUBLIC
                ? ClinicServiceBookingQueryService.BookingAudience.PUBLIC_PATIENT
                : ClinicServiceBookingQueryService.BookingAudience.RECEPTIONIST;
        var service = services.findActiveService(query.serviceId(), bookingAudience);

        Map<UUID, StaffAvailabilityProviderQueryService.AvailabilityProvider> acceptingProviders = providers
                .findAcceptingDoctorsInActiveDirectory().stream()
                .filter(provider -> query.audience() == AppointmentAvailabilityQuery.Audience.STAFF
                        || provider.publiclyVisible())
                .collect(Collectors.toMap(StaffAvailabilityProviderQueryService.AvailabilityProvider::doctorProfileId,
                        Function.identity(), (left, right) -> left));
        Set<UUID> activelyAssigned = Set.copyOf(assignments.findActiveDoctorProfileIds(service.serviceId()));
        Map<UUID, StaffAvailabilityProviderQueryService.AvailabilityProvider> eligibleProviders = acceptingProviders
                .entrySet().stream().filter(entry -> activelyAssigned.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (query.doctorProfileId() != null && !eligibleProviders.containsKey(query.doctorProfileId())) {
            throw new AppointmentAvailabilityException("DOCTOR_NOT_ELIGIBLE");
        }
        Set<UUID> doctorIds = query.doctorProfileId() == null
                ? eligibleProviders.keySet()
                : Set.of(query.doctorProfileId());
        if (doctorIds.isEmpty()) return List.of();

        var slotRequest = new SlotAvailabilityRequest(doctorIds, query.fromDate(), query.toDate(),
                SlotServiceContext.current(service.serviceId(), service.consultationDuration(), service.bufferDuration()));
        List<AvailableSlot> slots = slotEngine.generate(slotRequest);
        Instant earliestStart = query.audience() == AppointmentAvailabilityQuery.Audience.PUBLIC
                ? now.plus(properties.onlineMinimumNoticeMinutes(), ChronoUnit.MINUTES)
                : now;
        return slots.stream()
                .filter(slot -> eligibleProviders.containsKey(slot.doctorProfileId()))
                .filter(slot -> !slot.startTime().isBefore(earliestStart))
                .map(slot -> {
                    var provider = eligibleProviders.get(slot.doctorProfileId());
                    return new AppointmentAvailabilitySlot(slot.serviceId(), slot.doctorProfileId(),
                            provider.displayName(), slot.startTime(), slot.endTime(), slot.occupiedUntil());
                }).toList();
    }

    private void validateWindow(AppointmentAvailabilityQuery query) {
        if (query == null || query.serviceId() == null || query.fromDate() == null || query.toDate() == null
                || query.audience() == null || query.fromDate().isAfter(query.toDate())) {
            throw new AppointmentAvailabilityException("VALIDATION_FAILED");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(query.fromDate(), query.toDate()) + 1;
        long maxInclusiveDays = (long) properties.maxHorizonDays() + 1;
        if (inclusiveDays > maxInclusiveDays) throw new AppointmentAvailabilityException("VALIDATION_FAILED");

        LocalDate today = LocalDate.now(clock.withZone(properties.clinicTimeZone()));
        if (query.fromDate().isBefore(today) || query.toDate().isAfter(today.plusDays(properties.maxHorizonDays()))) {
            throw new AppointmentAvailabilityException("BOOKING_WINDOW_VIOLATION");
        }
    }
}
