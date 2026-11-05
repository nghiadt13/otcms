package fu.fa26.sep490.g58.otcms_be.reporting;

import fu.fa26.sep490.g58.otcms_be.reporting.integration.openpdf.OpenPdfReportWriter;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DailyCount;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportResult;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceUsage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import static org.junit.jupiter.api.Assertions.*;

class OpenPdfReportWriterTest {
    private final OpenPdfReportWriter writer = new OpenPdfReportWriter();
    private final ReportingPeriod period = ReportingPeriod.parse("2026-01-01", "2026-01-02");

    @Test
    void appointmentPdfContainsVietnameseTextAndEmbeddedFonts() throws Exception {
        byte[] bytes = writer.write(report("APPOINTMENTS", List.of(new DailyCount("2026-01-01", 2)), List.of(), 2));
        try (var pdf = read(bytes)) {
            String text = new PdfTextExtractor(pdf).getTextFromPage(1);
            assertTrue(text.contains("BÁO CÁO PHÒNG KHÁM"), text);
            assertTrue(text.contains("Lịch hẹn theo ngày"), text);
            assertTrue(text.contains("2026-01-01"));
            assertTrue(text.contains("Tổng"));
            assertTrue(text.contains("Trang 1"));
            var fonts = pdf.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.FONT);
            assertFalse(fonts.getKeys().isEmpty());
            for (var key : fonts.getKeys()) {
                PdfDictionary font = fonts.getAsDict(key);
                var descendant = font.getAsArray(PdfName.DESCENDANTFONTS).getAsDict(0);
                var descriptor = descendant.getAsDict(PdfName.FONTDESCRIPTOR);
                assertNotNull(descriptor.get(PdfName.FONTFILE2), "Every font must embed its TrueType program");
            }
        }
    }

    @Test
    void emptyPeriodStillProducesAZeroReport() throws Exception {
        try (var pdf = read(writer.write(report("VISITS", List.of(), List.of(), 0)))) {
            String text = new PdfTextExtractor(pdf).getTextFromPage(1);
            assertTrue(text.contains("Lượt khám theo ngày"), text);
            assertTrue(text.contains("Không có hoạt động trong kỳ này. Tổng số: 0."), text);
        }
    }

    @Test
    void serviceReportUsesUnicodeServiceNamesAndRepeatsHeadersAcrossPages() throws Exception {
        var rows = IntStream.range(0, 100).mapToObj(index ->
            new ServiceUsage("service-" + index, "Khám chấn thương chỉnh hình " + index, "EXAMINATION", 1)).toList();
        try (var pdf = read(writer.write(report("SERVICES", List.of(), rows, 100)))) {
            assertTrue(pdf.getNumberOfPages() > 1);
            var extractor = new PdfTextExtractor(pdf);
            String first = extractor.getTextFromPage(1);
            assertTrue(first.contains("Dịch vụ khám từ lịch hẹn đã hoàn tất"), first);
            assertTrue(first.contains("Khám chấn thương chỉnh hình 0"), first);
            String last = extractor.getTextFromPage(pdf.getNumberOfPages());
            assertTrue(last.contains("Khám chấn thương chỉnh hình 99"), last);
            assertTrue(last.contains("Tổng"));
            assertTrue(last.contains("Trang " + pdf.getNumberOfPages()));
            assertTrue(last.contains("Dịch vụ"));
        }
    }

    private ReportResult report(String subject, List<DailyCount> daily, List<ServiceUsage> services, long total) {
        return new ReportResult(subject, period, null, Instant.parse("2026-01-03T02:00:00Z"), daily, services, total);
    }

    private PdfReader read(byte[] bytes) throws Exception {
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        return new PdfReader(bytes);
    }
}
