package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService.AvailabilityProvider;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffDirectoryQueryService.DoctorBookingProfile;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffDirectoryQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffScheduleAvailabilityService.DoctorSchedule;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffScheduleAvailabilityService.Interval;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffScheduleAvailabilityService;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorWorkShiftRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StaffAvailabilityQueryServiceImpl implements StaffDirectoryQueryService, StaffScheduleAvailabilityService,
        StaffAvailabilityProviderQueryService {
    private final StaffProfileRepository profiles;
    private final DoctorWorkShiftRepository shifts;

    public StaffAvailabilityQueryServiceImpl(StaffProfileRepository profiles, DoctorWorkShiftRepository shifts) {
        this.profiles = profiles;
        this.shifts = shifts;
    }

    @Override
    public List<StaffAvailabilityProviderQueryService.AvailabilityProvider> findAcceptingDoctorsInActiveDirectory() {
        return profiles.findAcceptingDoctors().stream()
                .map(profile -> new StaffAvailabilityProviderQueryService.AvailabilityProvider(
                        profile.id(), profile.displayName(), profile.publiclyVisible()))
                .toList();
    }

    @Override
    public Optional<String> findDoctorDisplayName(UUID doctorProfileId) {
        return profiles.findByIdAndDoctorTrue(doctorProfileId).map(StaffProfileJpaEntity::displayName);
    }

    @Override
    public DoctorBookingProfile findDoctorByAccount(UUID accountId, Instant requestedStart, Instant requestedEnd) {
        if (requestedStart == null || requestedEnd == null || !requestedStart.isBefore(requestedEnd)) {
            throw new IllegalArgumentException("Requested interval must have positive duration");
        }
        Optional<StaffProfileJpaEntity> found = profiles.findByAccountIdAndDoctorTrue(accountId);
        if (found.isEmpty()) {
            return null;
        }
        StaffProfileJpaEntity profile = found.get();
        UUID doctorId = profile.id();
        boolean shiftCovered = shifts.existsByDoctorProfileIdAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                doctorId, requestedStart, requestedEnd);
        return new DoctorBookingProfile(doctorId, profile.publiclyVisible(), profile.acceptingNewBookings(),
                profile.inActiveDirectory(), shiftCovered);
    }

    @Override
    public List<DoctorSchedule> findSchedules(Collection<UUID> doctorProfileIds,
                                               Instant fromInclusive, Instant toExclusive) {
        if (doctorProfileIds == null || doctorProfileIds.isEmpty()
                || doctorProfileIds.stream().anyMatch(id -> id == null)
                || fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("Doctor IDs and a positive bounded interval are required");
        }
        List<UUID> doctorIds = doctorProfileIds.stream().distinct().sorted().toList();
        Map<UUID, List<StaffScheduleAvailabilityService.Interval>> shiftsByDoctor = new HashMap<>();
        doctorIds.forEach(id -> shiftsByDoctor.put(id, new ArrayList<>()));
        shifts.findOverlapping(doctorIds, fromInclusive, toExclusive).forEach(shift ->
                shiftsByDoctor.get(shift.doctorProfileId()).add(
                        new StaffScheduleAvailabilityService.Interval(shift.startsAt(), shift.endsAt())));
        return doctorIds.stream().map(id -> new StaffScheduleAvailabilityService.DoctorSchedule(id,
                        shiftsByDoctor.get(id)))
                .sorted(Comparator.comparing(StaffScheduleAvailabilityService.DoctorSchedule::doctorProfileId))
                .toList();
    }
}
