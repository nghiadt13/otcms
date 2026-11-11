package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@EnabledIfEnvironmentVariable(named = "OTCMS_MINIO_TEST_ENABLED", matches = "true")
class LocalS3ReportStorageMinioIntegrationTest {
    @Test
    void uploadsPendingReportToPrivateBucket() throws Exception {
        URI endpoint = URI.create("http://127.0.0.1:19000");
        String bucket = "otcms-test-" + UUID.randomUUID();
        try (S3Client client = S3Client.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        System.getenv("OTCMS_MINIO_TEST_USER"), System.getenv("OTCMS_MINIO_TEST_PASSWORD"))))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            byte[] content = "%PDF-1.7\nsynthetic\n%%EOF".getBytes(StandardCharsets.UTF_8);
            var adapter = new LocalS3ReportStorageAdapter(client, bucket,
                    ignoredContent -> ReportValidationStatus.PENDING);
            var report = adapter.storePrivateReport(UUID.randomUUID(), UUID.randomUUID(), "synthetic-study", 1,
                    new ByteArrayInputStream(content), content.length);
            assertEquals(ReportValidationStatus.PENDING, report.validationStatus());
            String key = "diagnostic-reports/" + report.reportId() + ".pdf";
            byte[] stored = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toBytes()).asByteArray();
            assertArrayEquals(content, stored);
            var request = HttpRequest.newBuilder(endpoint.resolve("/" + bucket + "/" + key)).GET().build();
            int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            assertEquals(403, status);
        }
    }
}
