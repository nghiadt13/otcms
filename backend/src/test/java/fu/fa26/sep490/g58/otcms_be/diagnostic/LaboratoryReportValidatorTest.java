package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav.ClamAvScanner;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.pdf.PdfBoxClamAvReportValidator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class LaboratoryReportValidatorTest {
    @Test
    void acceptsOnlyCleanParseablePdfReports() throws Exception {
        var validator = new PdfBoxClamAvReportValidator(bytes -> ClamAvScanner.ScanResult.CLEAN);

        assertEquals(ReportValidationStatus.VALIDATED, validator.validate(pdf()));
        assertEquals(ReportValidationStatus.INVALID, validator.validate(png()));
        assertEquals(ReportValidationStatus.INVALID, validator.validate(new byte[] {1, 2}));
    }

    @Test
    void scannerUnavailabilityNeverPassesReport() throws Exception {
        var validator = new PdfBoxClamAvReportValidator(bytes -> ClamAvScanner.ScanResult.UNAVAILABLE);

        assertEquals(ReportValidationStatus.PENDING, validator.validate(pdf()));
        assertEquals(ReportValidationStatus.PENDING, validator.validate(new byte[] {1}));
    }

    private static byte[] pdf() throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] png() throws IOException {
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
            return output.toByteArray();
        }
    }
}
