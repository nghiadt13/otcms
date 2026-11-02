package fu.fa26.sep490.g58.otcms_be.reporting;
import fu.fa26.sep490.g58.otcms_be.reporting.domain.ReportingPeriod;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReportingPeriodTest {
    @Test void inclusiveNinetyDaysAndVietnamMidnightAreStable() {
        var p=ReportingPeriod.parse("2026-01-01","2026-03-31");
        assertEquals("2025-12-31T17:00:00Z",p.start().toString());
        assertEquals("2026-03-31T17:00:00Z",p.until().toString());
        assertEquals(p.from().minusDays(1),p.previous().to());
        assertThrows(IllegalArgumentException.class,()->ReportingPeriod.parse("2026-01-01","2026-04-01"));
    }
}
