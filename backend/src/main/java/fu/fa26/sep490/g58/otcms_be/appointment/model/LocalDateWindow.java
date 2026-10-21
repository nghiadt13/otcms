package fu.fa26.sep490.g58.otcms_be.appointment.model;


import fu.fa26.sep490.g58.otcms_be.appointment.exception.SlotAvailabilityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Inclusive clinic-local dates represented as a half-open UTC instant interval. */
public class LocalDateWindow {
    private final LocalDate fromInclusive;
    private final LocalDate toInclusive;
    private final Instant startInclusive;
    private final Instant endExclusive;
    public LocalDateWindow(LocalDate fromInclusive, LocalDate toInclusive, Instant startInclusive, Instant endExclusive) {
        this.fromInclusive = fromInclusive;
        this.toInclusive = toInclusive;
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
    }
    public LocalDate getFromInclusive() { return fromInclusive; }
    public LocalDate fromInclusive() { return fromInclusive; }
    public LocalDate getToInclusive() { return toInclusive; }
    public LocalDate toInclusive() { return toInclusive; }
    public Instant getStartInclusive() { return startInclusive; }
    public Instant startInclusive() { return startInclusive; }
    public Instant getEndExclusive() { return endExclusive; }
    public Instant endExclusive() { return endExclusive; }
    public static LocalDateWindow resolve(LocalDate from, LocalDate to, ZoneId zone, long maxInclusiveDates) {
        if (from == null || to == null || zone == null || maxInclusiveDates < 1 || from.isAfter(to)) {
            throw new SlotAvailabilityException("VALIDATION_FAILED");
        }
        long requestedDates = ChronoUnit.DAYS.between(from, to) + 1;
        if (requestedDates > maxInclusiveDates) throw new SlotAvailabilityException("VALIDATION_FAILED");
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        return new LocalDateWindow(from, to, start, end);
    }

    public boolean isEmpty() { return !startInclusive.isBefore(endExclusive); }
}
