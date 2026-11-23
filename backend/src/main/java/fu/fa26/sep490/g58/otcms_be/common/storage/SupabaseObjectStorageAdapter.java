package fu.fa26.sep490.g58.otcms_be.common.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Supabase Storage through its S3 protocol endpoint, one private bucket. The S3 client is not a Spring bean. */
public final class SupabaseObjectStorageAdapter implements ObjectStoragePort, AutoCloseable {
    private final S3Client client;
    private final String bucket;

    public SupabaseObjectStorageAdapter(URI endpoint, String region, String accessKey, String secretKey, String bucket) {
        String host = endpoint.getHost();
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || host == null
                || !host.toLowerCase(Locale.ROOT).endsWith(".storage.supabase.co")
                || !"/storage/v1/s3".equals(endpoint.getPath())
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || isBlank(accessKey) || isBlank(secretKey) || isBlank(region) || isBlank(bucket)) {
            throw new IllegalArgumentException("Invalid hosted Supabase Storage S3 configuration");
        }
        this.bucket = bucket;
        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(15))
                        .apiCallTimeout(Duration.ofSeconds(45))
                        .build())
                .build();
    }

    @Override
    public void put(String key, String contentType, byte[] content) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(ObjectStoragePort.requireSafeKey(key))
                .contentType(contentType).build(), RequestBody.fromBytes(content));
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket)
                    .key(ObjectStoragePort.requireSafeKey(key)).build()).asByteArray());
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(ObjectStoragePort.requireSafeKey(key)).build());
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(ObjectStoragePort.requireSafeKey(key)).build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return false;
            throw exception;
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
