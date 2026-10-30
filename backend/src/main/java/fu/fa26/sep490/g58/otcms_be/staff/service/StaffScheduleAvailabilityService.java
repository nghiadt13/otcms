package fu.fa26.sep490.g58.otcms_be.staff.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Staff-owned, bounded shift and operational-time-off intervals for slot calculation. */
    public interface StaffScheduleAvailabilityService {
    List<DoctorSchedule> findSchedules(Collection<UUID> doctorProfileIds,
                                       Instant fromInclusive, Instant toExclusive);

        static class DoctorSchedule {
    private final UUID doctorProfileId;
    private final List<Interval> shifts;
    private final List<Interval> timeOff;
    public DoctorSchedule(UUID doctorProfileId, List<Interval> shifts, List<Interval> timeOff) {
        shifts = List.copyOf(shifts);
            timeOff = List.copyOf(timeOff);
        this.doctorProfileId = doctorProfileId;
        this.shifts = shifts;
        this.timeOff = timeOff;
    }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public List<Interval> getShifts() { return shifts; }
    public List<Interval> shifts() { return shifts; }
    public List<Interval> getTimeOff() { return timeOff; }
    public List<Interval> timeOff() { return timeOff; }
}

        static class Interval {
    private final Instant startsAt;
    private final Instant endsAt;
    public Interval(Instant startsAt, Instant endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
    public Instant getStartsAt() { return startsAt; }
    public Instant startsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant endsAt() { return endsAt; }
}
}
