package fu.fa26.sep490.g58.otcms_be.appointment.model;


import java.time.Instant;

public class AvailabilityInterval {
    private final Instant startsAt;
    private final Instant endsAt;
    public AvailabilityInterval(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("Availability interval must be a positive half-open range");
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
    public Instant getStartsAt() { return startsAt; }
    public Instant startsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant endsAt() { return endsAt; }
    public boolean overlaps(AvailabilityInterval other) {
        return startsAt.isBefore(other.endsAt) && endsAt.isAfter(other.startsAt);
    }

    public boolean contains(AvailabilityInterval other) {
        return !other.startsAt.isBefore(startsAt) && !other.endsAt.isAfter(endsAt);
    }
}
