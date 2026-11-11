package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.local;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StoredReport;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportContentValidator;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local-only private report storage for development and demo workflows. */
@Component
@Profile("!prod & !supabase")
@ConditionalOnProperty(name = "app.diagnostic.report-storage.provider", havingValue = "local", matchIfMissing = true)
public final class LocalReportStorageAdapter implements ReportStoragePort {
    private static final long MAX_REPORT_BYTES = 25L * 1024 * 1024;
    private static final String STORAGE_REF_PREFIX = "local-private-report:";

    private final Path root;
    private final ReportContentValidator validator;

    public LocalReportStorageAdapter(
            @Value("${app.diagnostic.report-storage.path:${user.home}/.otcms/private-diagnostic-reports}") String root,
            ReportContentValidator validator) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.validator = java.util.Objects.requireNonNull(validator);
    }

    @Override
    public StoredReport storePrivateReport(UUID requestId, UUID visitId, String studyRef,
            int reportVersion, InputStream content, long contentLength) {
        if (requestId == null || visitId == null || studyRef == null || studyRef.isBlank()
                || reportVersion < 1 || content == null || contentLength < 1 || contentLength > MAX_REPORT_BYTES) {
            throw new IllegalArgumentException("Invalid local diagnostic report");
        }

        UUID reportId = UUID.randomUUID();
        Path file = reportPath(reportId);
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IOException("Report storage directory cannot be a symlink");
            try (OutputStream output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                copyBounded(content, output, contentLength);
            }
            ReportValidationStatus validation = validateStoredReport(reportId);
            return new StoredReport(reportId, requestId, visitId, studyRef, reportVersion,
                    STORAGE_REF_PREFIX + reportId, validation);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Keep the original storage error as the cause.
            }
            throw new IllegalStateException("Could not store private diagnostic report", exception);
        }
    }

    @Override
    public ReportValidationStatus validateStoredReport(UUID reportId) {
        Path file = reportPath(reportId);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return ReportValidationStatus.INVALID;
            long size = Files.size(file);
            if (size < 1 || size > MAX_REPORT_BYTES) return ReportValidationStatus.INVALID;
            return validator.validate(Files.readAllBytes(file));
        } catch (IOException exception) {
            return ReportValidationStatus.PENDING;
        }
    }

    @Override
    public InputStream openReleasedReport(UUID reportId, UUID actorId) {
        if (actorId == null) throw new IllegalArgumentException("Authorized actor is required");
        Path file = reportPath(reportId);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Private diagnostic report is unavailable");
            }
            return Files.newInputStream(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not open private diagnostic report", exception);
        }
    }

    private Path reportPath(UUID reportId) {
        if (reportId == null) throw new IllegalArgumentException("Report id is required");
        Path candidate = root.resolve(reportId + ".pdf").normalize();
        if (!candidate.startsWith(root)) throw new IllegalArgumentException("Invalid report id");
        return candidate;
    }

    private void copyBounded(InputStream input, OutputStream output, long expectedLength) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > expectedLength || total > MAX_REPORT_BYTES) {
                throw new IOException("Diagnostic report exceeds declared size");
            }
            output.write(buffer, 0, read);
        }
        if (total != expectedLength) throw new IOException("Diagnostic report size does not match");
    }
}
