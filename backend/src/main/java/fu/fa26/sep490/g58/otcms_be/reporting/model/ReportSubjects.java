package fu.fa26.sep490.g58.otcms_be.reporting.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UC-78 report subjects and the breakdowns each one accepts (single source for validation and the subject list). */
public final class ReportSubjects {
    private static final Map<String, List<String>> BREAKDOWNS = new LinkedHashMap<>();
    static {
        BREAKDOWNS.put("APPOINTMENTS", List.of("DATE"));
        BREAKDOWNS.put("VISITS", List.of("DATE"));
        BREAKDOWNS.put("SERVICES", List.of());
        BREAKDOWNS.put("IMAGING", List.of("DATE"));
        // Laboratory is owned by another team and has no source yet, so there is no LAB subject.
        BREAKDOWNS.put("PAYMENTS", List.of("DATE", "RECEPTIONIST", "METHOD"));
        BREAKDOWNS.put("PENDING_CANCELLED_ORDERS", List.of());
        BREAKDOWNS.put("REVIEWS", List.of("DATE"));
    }
    private ReportSubjects() {
    }
    public static List<String> codes() {
        return List.copyOf(BREAKDOWNS.keySet());
    }
    public static List<String> breakdowns(String subject) {
        return BREAKDOWNS.getOrDefault(subject, List.of());
    }
    /** An empty breakdown is always accepted; otherwise it must be one the subject lists. */
    public static boolean accepts(String subject, String breakdown) {
        return subject != null && BREAKDOWNS.containsKey(subject)
            && (breakdown == null || breakdown.isEmpty() || breakdowns(subject).contains(breakdown));
    }
}
