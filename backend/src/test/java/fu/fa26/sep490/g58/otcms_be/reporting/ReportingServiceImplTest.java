package fu.fa26.sep490.g58.otcms_be.reporting;
import fu.fa26.sep490.g58.otcms_be.reporting.model.*;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.ReportingRepository;
import fu.fa26.sep490.g58.otcms_be.reporting.service.impl.ReportingServiceImpl;
import fu.fa26.sep490.g58.otcms_be.reporting.exception.ReportingOperationException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReportingServiceImplTest {
    private final ReportingRepository repository = mock(ReportingRepository.class);
    private final GetClinicServiceCatalogService catalog = mock(GetClinicServiceCatalogService.class);
    private final ReportingServiceImpl service = new ReportingServiceImpl(repository, catalog);
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
    @Test void unsupportedSubjectsAndBreakdownsAreBusinessFailuresBeforePersistence() {
        assertThrows(ReportingOperationException.class, () -> service.report("PAYMENTS", period, null));
        assertThrows(ReportingOperationException.class, () -> service.report("SERVICES", period, "DATE"));
        verifyNoInteractions(repository, catalog);
    }
}
