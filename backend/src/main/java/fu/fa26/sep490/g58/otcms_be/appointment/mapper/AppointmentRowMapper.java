package fu.fa26.sep490.g58.otcms_be.appointment.mapper;

import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Converts native JPA query results into appointment projections. */
public final class AppointmentRowMapper {
    private AppointmentRowMapper() { }

    public static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.time.ZonedDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.time.LocalDateTime dateTime) return dateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected JPA timestamp type: " + value.getClass().getName());
    }

    public static UUID uuid(Object value) { return (UUID) value; }
    public static int integer(Object value) { return ((Number) value).intValue(); }

    public static CreatedAppointment createdAppointment(Object[] row) {
        return new CreatedAppointment(uuid(row[0]), uuid(row[1]), uuid(row[2]), uuid(row[3]), (String) row[4],
                (String) row[5], (String) row[6], instant(row[7]), instant(row[8]), integer(row[9]), integer(row[10]),
                instant(row[11]), uuid(row[12]), uuid(row[13]), instant(row[14]), instant(row[15]));
    }
}
