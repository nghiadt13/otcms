package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.minio;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** S3-compatible connection to the private local MinIO report bucket. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.diagnostic.report-storage.provider", havingValue = "minio")
public class MinioReportStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client minioDiagnosticReportS3Client(
            @Value("${app.diagnostic.report-storage.minio.endpoint}") URI endpoint,
            @Value("${app.diagnostic.report-storage.minio.region}") String region,
            @Value("${app.diagnostic.report-storage.minio.access-key}") String accessKey,
            @Value("${app.diagnostic.report-storage.minio.secret-key}") String secretKey) {
        String scheme = endpoint.getScheme();
        if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || (endpoint.getPath() != null && !endpoint.getPath().isEmpty() && !"/".equals(endpoint.getPath()))
                || region.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalArgumentException("Invalid MinIO report storage configuration");
        }
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(15))
                        .apiCallTimeout(Duration.ofSeconds(45))
                        .build())
                .build();
    }
}
