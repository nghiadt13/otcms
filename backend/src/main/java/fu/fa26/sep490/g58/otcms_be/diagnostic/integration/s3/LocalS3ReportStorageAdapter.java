package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.s3;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StoredReport;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportContentValidator;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/** Keeps reports private until PDFBox and ClamAV validation succeeds. */
@Component
@Profile("!prod & !supabase")
@ConditionalOnProperty(name = "app.diagnostic.report-storage.provider", havingValue = "s3-local")
public final class LocalS3ReportStorageAdapter implements ReportStoragePort {
    private static final int MAX_BYTES = 25 * 1024 * 1024;
    private final S3Client client;
    private final String bucket;
    private final ReportContentValidator validator;

    public LocalS3ReportStorageAdapter(S3Client client,
            @Value("${app.diagnostic.report-storage.s3.bucket}") String bucket,
            ReportContentValidator validator) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("Report bucket is required");
        this.client = java.util.Objects.requireNonNull(client);
        this.bucket = bucket;
        this.validator = java.util.Objects.requireNonNull(validator);
    }

    @Override
    public StoredReport storePrivateReport(UUID requestId, UUID visitId, String studyRef,
            int reportVersion, InputStream content, long contentLength) {
        if (requestId == null || visitId == null || studyRef == null || studyRef.isBlank()
                || reportVersion < 1 || content == null || contentLength < 1 || contentLength > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid diagnostic report");
        }
        byte[] bytes;
        try {
            bytes = content.readNBytes((int) contentLength + 1);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read diagnostic report", exception);
        }
        if (bytes.length != contentLength) throw new IllegalArgumentException("Report size does not match");
        UUID reportId = UUID.randomUUID();
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key(reportId))
                .contentType("application/pdf").build(), RequestBody.fromBytes(bytes));
        return new StoredReport(reportId, requestId, visitId, studyRef, reportVersion,
                "s3-private-report:" + reportId, ReportValidationStatus.PENDING);
    }

    @Override
    public ReportValidationStatus validateStoredReport(UUID reportId) {
        if (reportId == null) throw new IllegalArgumentException("Report id is required");
        try (InputStream input = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key(reportId)).build())) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) return ReportValidationStatus.INVALID;
            return validator.validate(bytes);
        } catch (IOException | RuntimeException exception) {
            return ReportValidationStatus.PENDING;
        }
    }

    @Override
    public InputStream openReleasedReport(UUID reportId, UUID actorId) {
        if (actorId == null || reportId == null) throw new IllegalArgumentException("Authorized actor is required");
        // This local S3 binding has no release/read contract yet. Keep fail-closed.
        throw new IllegalStateException("Report release is unavailable for the local S3 adapter");
    }

    private String key(UUID reportId) {
        return "diagnostic-reports/" + reportId + ".pdf";
    }
}
