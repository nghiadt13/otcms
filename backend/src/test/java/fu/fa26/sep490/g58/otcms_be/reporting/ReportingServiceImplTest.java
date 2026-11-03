package fu.fa26.sep490.g58.otcms_be.reporting;
import fu.fa26.sep490.g58.otcms_be.reporting.model.*;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.ReportingRepository;
import fu.fa26.sep490.g58.otcms_be.reporting.service.impl.ReportingServiceImpl;
import fu.fa26.sep490.g58.otcms_be.reporting.exception.ReportingOperationException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.mockito.ArgumentCaptor;
import fu.fa26.sep490.g58.otcms_be.reporting.service.ReportPdfWriter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReportingServiceImplTest {
    private final ReportingRepository repository = mock(ReportingRepository.class);
    private final GetClinicServiceCatalogService catalog = mock(GetClinicServiceCatalogService.class);
    private final ReportPdfWriter writer = mock(ReportPdfWriter.class);
    private final ReportingServiceImpl service = new ReportingServiceImpl(repository, catalog, writer);
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

    @Test
    void exportsValidatedReportExactlyOnceWithSafeFileName() {
        var rows = List.of(new DailyCount("2026-01-01", 3));
        when(repository.daily("APPOINTMENTS", period)).thenReturn(rows);
        byte[] pdf = "%PDF-test".getBytes(StandardCharsets.US_ASCII);
        when(writer.write(any())).thenReturn(pdf);
        var file = service.exportPdf("APPOINTMENTS", period, "DATE");
        assertEquals("otcms-appointments-2026-01-01-2026-01-02.pdf", file.fileName());
        assertArrayEquals(pdf, file.content());
        var argument = ArgumentCaptor.forClass(ReportResult.class);
        verify(writer, times(1)).write(argument.capture());
        var result = argument.getValue();
        assertEquals("APPOINTMENTS", result.subject());
        assertEquals(period, result.period());
        assertEquals("DATE", result.breakdown());
        assertEquals(rows, result.daily());
        assertEquals(3, result.total());
        verify(repository, times(1)).daily("APPOINTMENTS", period);
    }

    @Test
    void invalidExportDoesNotCallWriter() {
        for (String subject : List.of("bad\r\nInjected: yes", "PAYMENTS")) {
            var error = assertThrows(ReportingOperationException.class,
                () -> service.exportPdf(subject, period, null));
            assertEquals(ReportingOperationException.Reason.INVALID_REPORT, error.reason());
        }
        var error = assertThrows(ReportingOperationException.class,
            () -> service.exportPdf("SERVICES", period, "DATE"));
        assertEquals(ReportingOperationException.Reason.INVALID_REPORT, error.reason());
        verifyNoInteractions(writer, repository, catalog);
    }

    @Test
    void writerFailureBecomesExportUnavailable() {
        when(repository.daily("VISITS", period)).thenReturn(List.of());
        when(writer.write(any())).thenThrow(new IllegalStateException("Internal renderer detail"));
        var error = assertThrows(ReportingOperationException.class,
            () -> service.exportPdf("VISITS", period, null));
        assertEquals(ReportingOperationException.Reason.EXPORT_UNAVAILABLE, error.reason());
        assertEquals("MSG-04", error.getMessage());
        verify(writer, times(1)).write(any());
    }
}
