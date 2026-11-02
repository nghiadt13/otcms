package fu.fa26.sep490.g58.otcms_be.reporting.application;

import fu.fa26.sep490.g58.otcms_be.reporting.domain.ReportingPeriod;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportingQueries implements ReportingUseCase {
    private final ReportingReader reader;
    private final ReportPdfWriter pdf;

    public ReportingQueries(ReportingReader reader, ReportPdfWriter pdf) {
        this.reader = reader;
        this.pdf = pdf;
    }

    @Override
    public byte[] exportPdf(String subject, ReportingPeriod period, String breakdown) {
        return pdf.write(report(subject, period, breakdown));
    }

    private Map<String, String> period(ReportingPeriod period) {
        return Map.of("from", period.from().toString(), "to", period.to().toString());
    }

    private Map<String, Object> metric(long current, Long previous) {
        var result = new LinkedHashMap<String, Object>();
        result.put("value", current);
        result.put("previousValue", previous);
        return result;
    }

    private long total(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private Map<String, Object> block(Map<String, Long> current, Map<String, Long> previous,
                                      List<String> codes) {
        var rows = new ArrayList<Map<String, Object>>();
        for (String code : codes) {
            var row = new LinkedHashMap<>(metric(current.getOrDefault(code, 0L), previous.getOrDefault(code, 0L)));
            row.put("code", code);
            rows.add(row);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "READY");
        result.put("total", metric(total(current), total(previous)));
        result.put("byStatus", rows);
        return result;
    }

    @Override
    public Map<String, Object> dashboard(ReportingPeriod period) {
        var previous = period.previous();
        var result = new LinkedHashMap<String, Object>();
        result.put("period", period(period));
        result.put("previousPeriod", period(previous));
        result.put("appointments", block(reader.appointmentCounts(period), reader.appointmentCounts(previous),
                List.of("BOOKED", "CHECKED_IN", "CANCELLED", "NO_SHOW")));
        var visits = block(reader.visitCounts(period), reader.visitCounts(previous),
                List.of("WAITING", "IN_PROGRESS", "COMPLETED", "CANCELLED"));
        // Room reservations track occupancy, not a stable room waiting-list read model.
        visits.put("waitingByRoom", List.of());
        visits.put("waitingByRoomStatus", "NO_SOURCE");
        result.put("visits", visits);

        var earlier = new HashMap<String, Long>();
        for (var row : reader.services(previous)) {
            earlier.put(row.get("serviceId").toString(), ((Number) row.get("value")).longValue());
        }
        var services = reader.services(period).stream().limit(10).map(row -> {
            var value = new LinkedHashMap<>(row);
            value.put("previousValue", earlier.getOrDefault(row.get("serviceId").toString(), 0L));
            return value;
        }).toList();
        result.put("services", Map.of("status", "READY", "top", services));
        // Billing and clinical source contracts are integrated separately; no synthetic zeros.
        for (String key : List.of("imagingAndLab", "payments", "pendingAndCancelled", "reviews")) {
            result.put(key, Map.of("status", "NO_SOURCE"));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> subjects() {
        return List.of(subject("APPOINTMENTS", "Lịch hẹn theo ngày"), subject("VISITS", "Lượt khám theo ngày"),
                Map.of("code", "SERVICES", "label", "Dịch vụ khám theo lịch hẹn đã hoàn tất",
                        "breakdownOptions", List.of()));
    }

    private Map<String, Object> subject(String code, String label) {
        return Map.of("code", code, "label", label, "breakdownOptions", List.of("DATE"));
    }

    @Override
    public Map<String, Object> report(String subject, ReportingPeriod period, String breakdown) {
        if (subject == null || !Set.of("APPOINTMENTS", "VISITS", "SERVICES").contains(subject)
                || (breakdown != null && !breakdown.isEmpty()
                && (!breakdown.equals("DATE") || subject.equals("SERVICES")))) {
            throw new IllegalArgumentException("MSG-72");
        }
        List<Map<String, Object>> rows;
        List<Map<String, String>> columns;
        if (subject.equals("SERVICES")) {
            rows = reader.services(period).stream().map(row -> Map.<String, Object>of(
                    "name", row.get("name"), "count", row.get("value"))).toList();
            columns = List.of(Map.of("key", "name", "label", "Dịch vụ", "kind", "text"),
                    Map.of("key", "count", "label", "Lịch hẹn đã hoàn tất", "kind", "count"));
        } else {
            rows = reader.daily(subject, period);
            columns = List.of(Map.of("key", "date", "label", "Ngày", "kind", "date"),
                    Map.of("key", "count", "label", "Số lượng", "kind", "count"));
        }
        long sum = rows.stream().mapToLong(row -> ((Number) row.get("count")).longValue()).sum();
        var query = new LinkedHashMap<String, Object>();
        query.put("subject", subject);
        query.putAll(period(period));
        if (breakdown != null && !breakdown.isEmpty()) query.put("breakdown", breakdown);
        String title = subject.equals("SERVICES") ? "Dịch vụ khám từ lịch đã hoàn tất" : "Thống kê theo ngày";
        return Map.of("query", query, "hasData", sum > 0, "generatedAt", Instant.now(),
                "allowedActions", List.of("export"), "exportFormats", List.of("pdf"), "sections", List.of(Map.of("title", title,
                        "columns", columns, "rows", rows, "totalRow", Map.of("count", sum))));
    }
}
