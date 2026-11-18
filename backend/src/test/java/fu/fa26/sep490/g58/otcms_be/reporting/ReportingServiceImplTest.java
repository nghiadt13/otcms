package fu.fa26.sep490.g58.otcms_be.reporting;
import fu.fa26.sep490.g58.otcms_be.reporting.model.*;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.ReviewStats;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.RoomWaiting;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.ReportingRepository;
import fu.fa26.sep490.g58.otcms_be.reporting.service.impl.ReportingServiceImpl;
import fu.fa26.sep490.g58.otcms_be.reporting.exception.ReportingOperationException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReportingServiceImplTest {
    private final ReportingRepository repository = mock(ReportingRepository.class);
    private final GetClinicServiceCatalogService catalog = mock(GetClinicServiceCatalogService.class);
    // 2026-11-18 18:00 UTC is already 19/11 in Vietnam: "today" must follow clinic time, not UTC.
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T18:00:00Z"), ZoneOffset.UTC);
    private final ReportingServiceImpl service = new ReportingServiceImpl(repository, catalog, clock);
    private final ReportingPeriod period = ReportingPeriod.parse("2026-01-01", "2026-01-02");
    @Test void historicalServiceMetadataComesFromTheOwnerIncludingInactiveServices() {
        UUID id = UUID.randomUUID();
        var entry = mock(ClinicService.class);
        when(entry.id()).thenReturn(id);
        when(entry.name()).thenReturn("Inactive historical service");
        when(entry.type()).thenReturn(ClinicServiceType.EXAMINATION);
        when(repository.services(period)).thenReturn(List.of(new ServiceCount(id.toString(), 9)));
        when(catalog.adminCatalog()).thenReturn(List.of(entry));
        var result = service.report("SERVICES", period, null);
        assertEquals(9, result.total());
        assertEquals("Inactive historical service", result.services().getFirst().name());
        assertEquals("EXAMINATION", result.services().getFirst().category());
        verify(catalog).adminCatalog();
    }
    @Test void dailyReportsDoNotReadCatalogOrBuildPresentationMetadata() {
        when(repository.daily("VISITS", period)).thenReturn(List.of(new DailyCount("2026-01-01", 3)));
        var result = service.report("VISITS", period, "DATE");
        assertEquals(3, result.total());
        assertEquals(List.of(new DailyCount("2026-01-01", 3)), result.daily());
        assertTrue(result.services().isEmpty());
        verifyNoInteractions(catalog);
    }
    @Test void imagingUsesTheDailyQueryAndTotalsIt() {
        when(repository.daily("IMAGING", period)).thenReturn(List.of(new DailyCount("2026-01-01", 2), new DailyCount("2026-01-02", 5)));
        assertEquals(7, service.report("IMAGING", period, "DATE").total());
    }
    @Test void paymentsDefaultToDateBreakdownAndTotalCountAndMoney() {
        when(repository.paymentRows(period, "DATE")).thenReturn(List.of(
            new ReportRow("2026-01-01", 2, 300_000L, null), new ReportRow("2026-01-02", 1, 150_000L, null)));
        var result = service.report("PAYMENTS", period, null);
        assertEquals("DATE", result.breakdown());
        assertEquals(3, result.total());
        assertEquals(450_000L, result.totalAmount());
        for (String breakdown : List.of("RECEPTIONIST", "METHOD")) {
            service.report("PAYMENTS", period, breakdown);
            verify(repository).paymentRows(period, breakdown);
        }
    }
    @Test void pendingCancelledOrdersAndReviewsTotalTheirRows() {
        when(repository.orderRows(period)).thenReturn(List.of(new ReportRow("PENDING_PAYMENT", 2, 200_000L, null),
            new ReportRow("CANCELLED", 1, 50_000L, null), new ReportRow("REFUND_PENDING", 1, 80_000L, null)));
        var orders = service.report("PENDING_CANCELLED_ORDERS", period, null);
        assertEquals(4, orders.total());
        assertEquals(330_000L, orders.totalAmount());
        when(repository.reviewRows(period)).thenReturn(List.of(new ReportRow("2026-01-01", 3, null, 4.5)));
        var reviews = service.report("REVIEWS", period, "DATE");
        assertEquals(3, reviews.total());
        assertNull(reviews.totalAmount());
    }
    @Test void unsupportedSubjectsAndBreakdownsAreBusinessFailuresBeforePersistence() {
        assertThrows(ReportingOperationException.class, () -> service.report("LAB", period, null));
        assertThrows(ReportingOperationException.class, () -> service.report(null, period, null));
        assertThrows(ReportingOperationException.class, () -> service.report("SERVICES", period, "DATE"));
        assertThrows(ReportingOperationException.class, () -> service.report("PAYMENTS", period, "WEEK"));
        assertThrows(ReportingOperationException.class, () -> service.report("PENDING_CANCELLED_ORDERS", period, "DATE"));
        verifyNoInteractions(repository, catalog);
    }
    @Test void subjectsListEveryTopicExceptLaboratory() {
        assertEquals(List.of("APPOINTMENTS", "VISITS", "SERVICES", "IMAGING", "PAYMENTS", "PENDING_CANCELLED_ORDERS", "REVIEWS"),
            service.subjects());
    }
    @Test void defaultPeriodIsTodayInClinicTimeNotUtc() {
        var today = service.today();
        assertEquals(LocalDate.parse("2026-11-19"), today.from());
        assertEquals(today.from(), today.to());
    }
    @Test void dashboardFillsEveryBlockWithPreviousPeriodComparison() {
        var current = ReportingPeriod.parse("2026-11-10", "2026-11-16");
        var previous = current.previous();
        when(repository.appointmentCounts(any())).thenReturn(Map.of());
        when(repository.visitCounts(any())).thenReturn(Map.of());
        when(repository.imagingPerformed(current)).thenReturn(4L);
        when(repository.imagingPerformed(previous)).thenReturn(1L);
        when(repository.paymentsByMethod(current)).thenReturn(Map.of("CASH", 300_000L, "PAYOS", 200_000L));
        when(repository.paymentsByMethod(previous)).thenReturn(Map.of("CASH", 100_000L));
        when(repository.orderCounts(current)).thenReturn(Map.of("PENDING_PAYMENT", 3L));
        when(repository.orderCounts(previous)).thenReturn(Map.of("CANCELLED", 2L));
        when(repository.refundPendingItems()).thenReturn(5L);
        when(repository.reviewStats(current)).thenReturn(new ReviewStats(2, 4.5));
        when(repository.reviewStats(previous)).thenReturn(new ReviewStats(0, null));
        when(repository.pendingReviews()).thenReturn(7L);
        when(repository.waitingByRoom(LocalDate.parse("2026-11-19"))).thenReturn(List.of(new RoomWaiting("r1", "Phòng 1", 3)));
        var summary = service.dashboard(current);
        assertEquals(new MetricValue(4, 1L), summary.imagingPerformed());
        assertEquals(new MetricValue(500_000L, 100_000L), summary.payments().collected());
        assertEquals(List.of(new StatusMetric("CASH", 300_000L, 100_000L), new StatusMetric("PAYOS", 200_000L, 0L)), summary.payments().byMethod());
        assertEquals(new MetricValue(3, 0L), summary.orders().pendingPayment());
        assertEquals(new MetricValue(0, 2L), summary.orders().cancelled());
        assertEquals(5, summary.orders().refundPending());
        assertEquals(4.5, summary.reviews().average());
        assertNull(summary.reviews().previousAverage());
        assertEquals(7, summary.reviews().pendingApproval());
        assertEquals("Phòng 1", summary.waitingByRoom().getFirst().roomName());
    }
}
