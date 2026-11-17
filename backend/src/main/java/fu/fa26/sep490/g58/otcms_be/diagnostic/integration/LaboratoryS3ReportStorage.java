package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public final class LaboratoryS3ReportStorage implements LaboratoryReportStoragePort {
    private final S3Client client;
    private final String bucket;
    private final LaboratoryReportValidator validator;
    public LaboratoryS3ReportStorage(S3Client client, String bucket, LaboratoryReportValidator validator) {
        this.client = java.util.Objects.requireNonNull(client);
        this.bucket = java.util.Objects.requireNonNull(bucket);
        this.validator = java.util.Objects.requireNonNull(validator);
    }
    @Override public Stored store(UUID orderId, byte[] contents, String contentType) {
        if (orderId == null || validator.validate(contents, contentType) != ReportValidationStatus.VALIDATED)
            throw new IllegalArgumentException("Report validation failed or scanner unavailable");
        UUID reportId = UUID.randomUUID();
        String key = key(orderId, reportId);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromBytes(contents));
            return new Stored(reportId, key, hash, contentType);
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    @Override public InputStream open(UUID orderId, UUID reportId, String storageRef) {
        if (orderId == null || reportId == null || !key(orderId, reportId).equals(storageRef)) throw new IllegalArgumentException("Invalid report reference");
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(storageRef).build());
    }
    private static String key(UUID orderId, UUID reportId) { return "laboratory-reports/" + orderId + "/" + reportId; }
}
