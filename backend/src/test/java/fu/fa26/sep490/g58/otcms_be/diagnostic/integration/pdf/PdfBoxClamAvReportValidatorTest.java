package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav.ClamAvScanner;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDDocumentCatalogAdditionalActions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PdfBoxClamAvReportValidatorTest {
    private ClamAvScanner scanner;
    private PdfBoxClamAvReportValidator validator;

    @BeforeEach
    void setUp() {
        scanner = Mockito.mock(ClamAvScanner.class);
        validator = new PdfBoxClamAvReportValidator(scanner);
    }

    @Test
    void onlyParseablePdfWithCleanScanIsValidated() throws Exception {
        byte[] pdf = validPdf();
        when(scanner.scan(pdf)).thenReturn(ClamAvScanner.ScanResult.CLEAN);

        assertEquals(ReportValidationStatus.VALIDATED, validator.validate(pdf));
    }

    @Test
    void scannerFailureLeavesValidPdfPending() throws Exception {
        byte[] pdf = validPdf();
        when(scanner.scan(pdf)).thenReturn(ClamAvScanner.ScanResult.UNAVAILABLE);

        assertEquals(ReportValidationStatus.PENDING, validator.validate(pdf));
    }

    @Test
    void infectedPdfIsRejectedBeforeParsing() throws Exception {
        byte[] pdf = validPdf();
        when(scanner.scan(pdf)).thenReturn(ClamAvScanner.ScanResult.INFECTED);

        assertEquals(ReportValidationStatus.INVALID, validator.validate(pdf));
    }

    @Test
    void malformedPdfIsRejectedAfterCleanScan() {
        byte[] malformed = "%PDF-not-a-document".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(scanner.scan(malformed)).thenReturn(ClamAvScanner.ScanResult.CLEAN);

        assertEquals(ReportValidationStatus.INVALID, validator.validate(malformed));
    }

    @Test
    void pdfWithJavascriptActionIsRejected() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            var actions = new PDDocumentCatalogAdditionalActions();
            actions.setWP(new PDActionJavaScript("app.alert('blocked')"));
            document.getDocumentCatalog().setActions(actions);
            document.save(output);
            pdf = output.toByteArray();
        }
        when(scanner.scan(pdf)).thenReturn(ClamAvScanner.ScanResult.CLEAN);

        assertEquals(ReportValidationStatus.INVALID, validator.validate(pdf));
    }

    @Test
    void emptyInputIsRejectedWithoutCallingScanner() {
        assertEquals(ReportValidationStatus.INVALID, validator.validate(new byte[0]));
        verify(scanner, never()).scan(Mockito.any());
    }

    private byte[] validPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
