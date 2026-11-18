package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav.ClamAvScanner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

/** Validate the actual file content before a laboratory report can be released. */
public final class LaboratoryReportValidator {
    private static final int MAX_BYTES = 25 * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private final ReportContentValidator pdfValidator;
    private final ClamAvScanner scanner;

    public LaboratoryReportValidator(ReportContentValidator pdfValidator, ClamAvScanner scanner) {
        this.pdfValidator = Objects.requireNonNull(pdfValidator);
        this.scanner = Objects.requireNonNull(scanner);
    }

    public ReportValidationStatus validate(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) return ReportValidationStatus.INVALID;
        if ("application/pdf".equals(contentType)) return pdfValidator.validate(bytes);
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) return ReportValidationStatus.INVALID;
        var scan = scanner.scan(bytes);
        if (scan == ClamAvScanner.ScanResult.INFECTED) return ReportValidationStatus.INVALID;
        if (scan != ClamAvScanner.ScanResult.CLEAN) return ReportValidationStatus.PENDING;
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) return ReportValidationStatus.INVALID;
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return ReportValidationStatus.INVALID;
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                var format = reader.getFormatName();
                if (!("image/jpeg".equals(contentType) && ("JPEG".equalsIgnoreCase(format) || "JPG".equalsIgnoreCase(format)))
                        && !("image/png".equals(contentType) && "PNG".equalsIgnoreCase(format))) return ReportValidationStatus.INVALID;
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) return ReportValidationStatus.INVALID;
                return reader.read(0) == null ? ReportValidationStatus.INVALID : ReportValidationStatus.VALIDATED;
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException failure) { return ReportValidationStatus.INVALID; }
    }
}
