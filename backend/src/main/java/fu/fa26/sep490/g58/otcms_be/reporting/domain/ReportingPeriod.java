package fu.fa26.sep490.g58.otcms_be.reporting.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Inclusive clinic dates, queried as a half-open interval in Vietnam time. */
public record ReportingPeriod(LocalDate from, LocalDate to) {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public ReportingPeriod {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) >= 90) {
            throw new IllegalArgumentException("MSG-72");
        }
    }

    public static ReportingPeriod parse(String from, String to) {
        if (from == null || to == null || !from.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !to.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("MSG-72");
        }
        return new ReportingPeriod(LocalDate.parse(from), LocalDate.parse(to));
    }

    public ReportingPeriod previous() {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new ReportingPeriod(from.minusDays(days), from.minusDays(1));
    }

    public Instant start() {
        return from.atStartOfDay(CLINIC_ZONE).toInstant();
    }

    public Instant until() {
        return to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
    }
}
