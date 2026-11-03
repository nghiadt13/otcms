package fu.fa26.sep490.g58.otcms_be.reporting.service.impl;

import fu.fa26.sep490.g58.otcms_be.reporting.exception.ReportingOperationException;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ActivityCounts;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DailyCount;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.MetricValue;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportResult;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceComparison;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceUsage;
import fu.fa26.sep490.g58.otcms_be.reporting.model.StatusMetric;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.ReportingRepository;
import fu.fa26.sep490.g58.otcms_be.reporting.service.ReportingService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportingServiceImpl implements ReportingService {
    private final ReportingRepository repository;
    private final fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService catalog;
    public ReportingServiceImpl(ReportingRepository repository, fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }
    @Override
    public DashboardSummary dashboard(ReportingPeriod period) {
        var previous = period.previous();
        var appointments = counts(repository.appointmentCounts(period), repository.appointmentCounts(previous),
            List.of("BOOKED", "CHECKED_IN", "CANCELLED", "NO_SHOW"));
        var visits = counts(repository.visitCounts(period), repository.visitCounts(previous),
            List.of("WAITING", "IN_PROGRESS", "COMPLETED", "CANCELLED"));
        var earlier = serviceUsage(previous).stream()
        .collect(Collectors.toMap(ServiceUsage::serviceId, ServiceUsage::value));
        var services = serviceUsage(period).stream().limit(10)
        .map(row -> new ServiceComparison(row.serviceId(), row.name(), row.category(), row.value(),
            earlier.getOrDefault(row.serviceId(), 0L))).toList();
        return new DashboardSummary(period, previous, appointments, visits, services);
    }
    @Override
    public List<String> subjects() {
        return List.of("APPOINTMENTS", "VISITS", "SERVICES");
    }
    @Override
    public ReportResult report(String subject, ReportingPeriod period, String breakdown) {
        if (subject == null || !Set.of("APPOINTMENTS", "VISITS", "SERVICES").contains(subject)
            || (breakdown != null && !breakdown.isEmpty()
            && (!breakdown.equals("DATE") || subject.equals("SERVICES")))) {
            throw new ReportingOperationException(ReportingOperationException.Reason.INVALID_REPORT);
        }
        var daily = subject.equals("SERVICES") ? List.<DailyCount>of() : repository.daily(subject, period);
        var services = subject.equals("SERVICES") ? serviceUsage(period) : List.<ServiceUsage>of();
        long total = subject.equals("SERVICES") ? services.stream().mapToLong(ServiceUsage::value).sum()
            : daily.stream().mapToLong(DailyCount::count).sum();
        return new ReportResult(subject, period, breakdown, Instant.now(), daily, services, total);
    }
    /** Reuse the owner's public catalog service, including inactive historical services. */
    private List<ServiceUsage> serviceUsage(ReportingPeriod period) {
        var counts = repository.services(period);
        if (counts.isEmpty()) {
            return List.of();
        }
        var entries = catalog.adminCatalog().stream().collect(Collectors.toMap(service -> service.id().toString(), service -> service));
        return counts.stream().filter(count -> entries.containsKey(count.serviceId())).map(count -> {
                var service = entries.get(count.serviceId());
                return new ServiceUsage(count.serviceId(), service.name(), service.type().name(), count.value());
        }).toList();
    }

    private ActivityCounts counts(Map<String, Long> current, Map<String, Long> previous, List<String> codes) {
        var rows = codes.stream().map(code -> new StatusMetric(code, current.getOrDefault(code, 0L),
            previous.getOrDefault(code, 0L))).toList();
        var total = new MetricValue(current.values().stream().mapToLong(Long::longValue).sum(),
            previous.values().stream().mapToLong(Long::longValue).sum());
        return new ActivityCounts(total, rows);
    }
}
