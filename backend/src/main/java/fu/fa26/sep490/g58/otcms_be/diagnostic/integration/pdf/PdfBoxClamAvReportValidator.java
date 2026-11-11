package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.pdf;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportContentValidator;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav.ClamAvScanner;
import java.io.IOException;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDDestinationOrAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionImportData;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

/** A report is valid only when clamd reports it clean and PDFBox can parse it. */
public final class PdfBoxClamAvReportValidator implements ReportContentValidator {
    private static final int MAX_REPORT_BYTES = 25 * 1024 * 1024;
    private final ClamAvScanner scanner;

    public PdfBoxClamAvReportValidator(ClamAvScanner scanner) {
        this.scanner = Objects.requireNonNull(scanner);
    }

    @Override
    public ReportValidationStatus validate(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_REPORT_BYTES) {
            return ReportValidationStatus.INVALID;
        }
        ClamAvScanner.ScanResult scan = scanner.scan(content);
        if (scan == ClamAvScanner.ScanResult.INFECTED) return ReportValidationStatus.INVALID;
        if (scan != ClamAvScanner.ScanResult.CLEAN) return ReportValidationStatus.PENDING;
        try (var document = Loader.loadPDF(content)) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1 || containsActiveContent(document)) {
                return ReportValidationStatus.INVALID;
            }
            return ReportValidationStatus.VALIDATED;
        } catch (IOException | RuntimeException exception) {
            return ReportValidationStatus.INVALID;
        }
    }

    private boolean containsActiveContent(PDDocument document) throws IOException {
        var catalog = document.getDocumentCatalog();
        var names = catalog.getNames();
        if (names != null && (names.getJavaScript() != null || names.getEmbeddedFiles() != null)) return true;
        PDDestinationOrAction openAction = catalog.getOpenAction();
        if (openAction instanceof PDAction action && containsUnsafeAction(action)) return true;
        var catalogActions = catalog.getActions();
        if (catalogActions != null && containsUnsafeAction(catalogActions.getWC(), catalogActions.getWS(),
                catalogActions.getDS(), catalogActions.getWP(), catalogActions.getDP())) return true;

        PDAcroForm form = catalog.getAcroForm();
        if (form != null) {
            for (PDField field : form.getFieldTree()) {
                var actions = field.getActions();
                if (actions != null && containsUnsafeAction(actions.getK(), actions.getF(), actions.getV(), actions.getC())) {
                    return true;
                }
                for (var widget : field.getWidgets()) {
                    var widgetActions = widget.getActions();
                    if (widgetActions != null && containsUnsafeAction(widgetActions.getE(), widgetActions.getX(),
                            widgetActions.getD(), widgetActions.getU(), widgetActions.getFo(), widgetActions.getBl(),
                            widgetActions.getPO(), widgetActions.getPC(), widgetActions.getPV(), widgetActions.getPI())) {
                        return true;
                    }
                }
            }
        }

        for (PDPage page : document.getPages()) {
            var pageActions = page.getActions();
            if (pageActions != null && containsUnsafeAction(pageActions.getO(), pageActions.getC())) return true;
            for (PDAnnotation annotation : page.getAnnotations()) {
                if (annotation instanceof PDAnnotationFileAttachment) return true;
                if (annotation instanceof PDAnnotationWidget widget) {
                    if (containsUnsafeAction(widget.getAction())) return true;
                    var actions = widget.getActions();
                    if (actions != null && containsUnsafeAction(actions.getE(), actions.getX(), actions.getD(),
                            actions.getU(), actions.getFo(), actions.getBl(), actions.getPO(), actions.getPC(),
                            actions.getPV(), actions.getPI())) return true;
                }
            }
        }
        return false;
    }

    private boolean containsUnsafeAction(PDAction... actions) {
        if (actions == null) return false;
        for (PDAction action : actions) {
            if (action == null) continue;
            var next = action.getNext();
            if (action instanceof PDActionJavaScript || action instanceof PDActionLaunch
                    || action instanceof PDActionImportData
                    || (next != null && containsUnsafeAction(next.toArray(PDAction[]::new)))) return true;
        }
        return false;
    }
}
