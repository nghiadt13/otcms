package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService.AvailabilityProvider;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffDirectoryQueryService.DoctorBookingProfile;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffDirectoryQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffScheduleAvailabilityService.DoctorSchedule;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffScheduleAvailabilityService.Interval;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffScheduleAvailabilityService;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorOperationalTimeOffRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
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
    private final DoctorProfileRepository profiles;
    private final DoctorWorkShiftRepository shifts;
    private final DoctorOperationalTimeOffRepository timeOff;

    public StaffAvailabilityQueryServiceImpl(DoctorProfileRepository profiles, DoctorWorkShiftRepository shifts,
                                   DoctorOperationalTimeOffRepository timeOff) {
        this.profiles = profiles;
        this.shifts = shifts;
        this.timeOff = timeOff;
    }

    @Override
    public List<StaffAvailabilityProviderQueryService.AvailabilityProvider> findAcceptingDoctorsInActiveDirectory() {
        return profiles.findAcceptingInActiveDirectory().stream()
                .map(profile -> new StaffAvailabilityProviderQueryService.AvailabilityProvider(
                        profile.id(), profile.fullName(), profile.publiclyVisible()))
                .toList();
    }

    @Override
    public Optional<String> findDoctorDisplayName(UUID doctorProfileId) {
        return profiles.findById(doctorProfileId).map(DoctorProfileJpaEntity::fullName);
    }

    @Override
    public DoctorBookingProfile findDoctorByAccount(UUID accountId, Instant requestedStart, Instant requestedEnd) {
        if (requestedStart == null || requestedEnd == null || !requestedStart.isBefore(requestedEnd)) {
            throw new IllegalArgumentException("Requested interval must have positive duration");
        }
        Optional<DoctorProfileJpaEntity> found = profiles.findByAccountId(accountId);
        if (found.isEmpty()) {
            return null;
        }
        DoctorProfileJpaEntity profile = found.get();
        UUID doctorId = profile.id();
        boolean shiftCovered = shifts.existsByDoctorProfileIdAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                doctorId, requestedStart, requestedEnd);
        boolean timeOffOverlap = timeOff.existsByDoctorProfileIdAndStartsAtLessThanAndEndsAtGreaterThan(
                doctorId, requestedEnd, requestedStart);
        return new DoctorBookingProfile(doctorId, profile.publiclyVisible(), profile.acceptingNewBookings(),
                profile.inActiveDirectory(), shiftCovered, timeOffOverlap);
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
        Map<UUID, List<StaffScheduleAvailabilityService.Interval>> timeOffByDoctor = new HashMap<>();
        doctorIds.forEach(id -> {
            shiftsByDoctor.put(id, new ArrayList<>());
            timeOffByDoctor.put(id, new ArrayList<>());
        });
        shifts.findOverlapping(doctorIds, fromInclusive, toExclusive).forEach(shift ->
                shiftsByDoctor.get(shift.doctorProfileId()).add(
                        new StaffScheduleAvailabilityService.Interval(shift.startsAt(), shift.endsAt())));
        timeOff.findOverlapping(doctorIds, fromInclusive, toExclusive).forEach(row ->
                timeOffByDoctor.get(row.doctorProfileId()).add(
                        new StaffScheduleAvailabilityService.Interval(row.startsAt(), row.endsAt())));
        return doctorIds.stream().map(id -> new StaffScheduleAvailabilityService.DoctorSchedule(id,
                        shiftsByDoctor.get(id), timeOffByDoctor.get(id)))
                .sorted(Comparator.comparing(StaffScheduleAvailabilityService.DoctorSchedule::doctorProfileId))
                .toList();
    }
}
