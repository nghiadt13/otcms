package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.supabase;

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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Stores private diagnostic PDFs in a private Supabase Storage bucket. */
@Component
@Profile("supabase & !prod")
@ConditionalOnProperty(name = "app.diagnostic.report-storage.provider", havingValue = "supabase-storage")
public final class SupabaseStorageReportAdapter implements ReportStoragePort {
    private static final int MAX_BYTES = 25 * 1024 * 1024;
    private static final String STORAGE_REF_PREFIX = "supabase-storage-private-report:";

    private final S3Client client;
    private final String bucket;
    private final ReportContentValidator validator;

    public SupabaseStorageReportAdapter(S3Client client,
            @Value("${app.diagnostic.report-storage.supabase-s3.bucket}") String bucket,
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
            bytes = content.readNBytes(MAX_BYTES + 1);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read diagnostic report", exception);
        }
        if (bytes.length != contentLength) throw new IllegalArgumentException("Report size does not match");

        UUID reportId = UUID.randomUUID();
        String objectKey = key(visitId, reportVersion, reportId);
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey)
                .contentType("application/pdf").build(), RequestBody.fromBytes(bytes));
        return new StoredReport(reportId, requestId, visitId, studyRef, reportVersion,
                STORAGE_REF_PREFIX + objectKey, ReportValidationStatus.PENDING);
    }

    @Override
    public ReportValidationStatus validateStoredReport(UUID reportId) {
        if (reportId == null) throw new IllegalArgumentException("Report id is required");
        return validateObject(legacyKey(reportId));
    }

    @Override
    public ReportValidationStatus validateStoredReport(UUID reportId, String storageRef) {
        if (reportId == null) throw new IllegalArgumentException("Report id is required");
        return validateObject(keyFromStorageRef(reportId, storageRef));
    }

    private ReportValidationStatus validateObject(String objectKey) {
        try (InputStream input = client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())) {
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
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(legacyKey(reportId)).build());
    }

    @Override
    public InputStream openReleasedReport(UUID reportId, String storageRef, UUID actorId) {
        if (actorId == null || reportId == null) throw new IllegalArgumentException("Authorized actor is required");
        return client.getObject(GetObjectRequest.builder().bucket(bucket)
                .key(keyFromStorageRef(reportId, storageRef)).build());
    }

    private String key(UUID visitId, int reportVersion, UUID reportId) {
        return "diagnostic-reports/visits/" + visitId + "/report-v" + reportVersion + "-" + reportId + ".pdf";
    }

    private String legacyKey(UUID reportId) {
        return "diagnostic-reports/" + reportId + ".pdf";
    }

    private String keyFromStorageRef(UUID reportId, String storageRef) {
        if (storageRef == null || !storageRef.startsWith(STORAGE_REF_PREFIX)) {
            throw new IllegalArgumentException("Invalid Supabase report reference");
        }
        String objectKey = storageRef.substring(STORAGE_REF_PREFIX.length());
        if (reportId.toString().equals(objectKey)) return legacyKey(reportId);
        String[] segments = objectKey.split("/", -1);
        if (segments.length == 2 && "diagnostic-reports".equals(segments[0])
                && (reportId + ".pdf").equals(segments[1])) {
            return objectKey;
        }
        if (segments.length != 4 || !"diagnostic-reports".equals(segments[0])
                || !"visits".equals(segments[1])) {
            throw new IllegalArgumentException("Invalid Supabase report reference");
        }
        UUID.fromString(segments[2]);
        String filenamePrefix = "report-v";
        String filenameSuffix = "-" + reportId + ".pdf";
        if (!segments[3].startsWith(filenamePrefix) || !segments[3].endsWith(filenameSuffix)) {
            throw new IllegalArgumentException("Invalid Supabase report reference");
        }
        String version = segments[3].substring(filenamePrefix.length(), segments[3].length() - filenameSuffix.length());
        try {
            if (Integer.parseInt(version) < 1) throw new IllegalArgumentException("Invalid Supabase report reference");
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Supabase report reference", exception);
        }
        return objectKey;
    }
}
