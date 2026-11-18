package fu.fa26.sep490.g58.otcms_be.reporting.service.impl;

import fu.fa26.sep490.g58.otcms_be.reporting.exception.ReportingOperationException;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ActivityCounts;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DailyCount;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.OrdersSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.PaymentsSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.ReviewStats;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.ReviewsSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.MetricValue;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportResult;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportRow;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportSubjects;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceComparison;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceUsage;
import fu.fa26.sep490.g58.otcms_be.reporting.model.StatusMetric;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.ReportingRepository;
import fu.fa26.sep490.g58.otcms_be.reporting.service.ReportingService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportingServiceImpl implements ReportingService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ReportingRepository repository;
    private final fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService catalog;
    private final Clock clock;
    public ReportingServiceImpl(ReportingRepository repository, fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService catalog, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.clock = clock;
    }
    @Override
    public ReportingPeriod today() {
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        return new ReportingPeriod(today, today);
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
        var imaging = new MetricValue(repository.imagingPerformed(period), repository.imagingPerformed(previous));
        var paid = repository.paymentsByMethod(period);
        var paidBefore = repository.paymentsByMethod(previous);
        var payments = new PaymentsSummary(
            new MetricValue(sum(paid), sum(paidBefore)),
            List.of("CASH", "PAYOS").stream().map(code -> new StatusMetric(code, paid.getOrDefault(code, 0L),
                paidBefore.getOrDefault(code, 0L))).toList());
        var orders = repository.orderCounts(period);
        var ordersBefore = repository.orderCounts(previous);
        var ordersSummary = new OrdersSummary(
            new MetricValue(orders.getOrDefault("PENDING_PAYMENT", 0L), ordersBefore.getOrDefault("PENDING_PAYMENT", 0L)),
            new MetricValue(orders.getOrDefault("CANCELLED", 0L), ordersBefore.getOrDefault("CANCELLED", 0L)),
            repository.refundPendingItems());
        ReviewStats rated = repository.reviewStats(period);
        var reviews = new ReviewsSummary(rated.average(), repository.reviewStats(previous).average(), repository.pendingReviews());
        var waiting = repository.waitingByRoom(LocalDate.now(clock.withZone(CLINIC_ZONE)));
        return new DashboardSummary(period, previous, appointments, visits, services, imaging, payments, ordersSummary,
            reviews, waiting);
    }
    @Override
    public List<String> subjects() {
        return ReportSubjects.codes();
    }
    @Override
    public ReportResult report(String subject, ReportingPeriod period, String breakdown) {
        if (!ReportSubjects.accepts(subject, breakdown)) {
            throw new ReportingOperationException(ReportingOperationException.Reason.INVALID_REPORT);
        }
        Instant now = clock.instant();
        return switch (subject) {
            case "SERVICES" -> {
                var services = serviceUsage(period);
                yield new ReportResult(subject, period, breakdown, now, List.of(), services, List.of(),
                    services.stream().mapToLong(ServiceUsage::value).sum(), null);
            }
            case "PAYMENTS" -> {
                String by = breakdown == null || breakdown.isEmpty() ? "DATE" : breakdown;
                yield rows(subject, period, by, now, repository.paymentRows(period, by), true);
            }
            case "PENDING_CANCELLED_ORDERS" -> rows(subject, period, breakdown, now, repository.orderRows(period), true);
            case "REVIEWS" -> rows(subject, period, breakdown, now, repository.reviewRows(period), false);
            default -> {
                var daily = repository.daily(subject, period);
                yield new ReportResult(subject, period, breakdown, now, daily, List.of(), List.of(),
                    daily.stream().mapToLong(DailyCount::count).sum(), null);
            }
        };
    }
    private static ReportResult rows(String subject, ReportingPeriod period, String breakdown, Instant now,
        List<ReportRow> rows, boolean money) {
        return new ReportResult(subject, period, breakdown, now, List.of(), List.of(), rows,
            rows.stream().mapToLong(ReportRow::count).sum(),
            money ? rows.stream().mapToLong(row -> row.amount() == null ? 0L : row.amount()).sum() : null);
    }
    private static long sum(Map<String, Long> values) {
        return values.values().stream().mapToLong(Long::longValue).sum();
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
