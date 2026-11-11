package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.supabase;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Server-side S3 protocol binding for hosted Supabase Storage. */
@Configuration(proxyBeanMethods = false)
@Profile("supabase & !prod")
@ConditionalOnProperty(name = "app.diagnostic.report-storage.provider", havingValue = "supabase-storage")
public class SupabaseStorageReportConfiguration {
    @Bean(destroyMethod = "close")
    S3Client supabaseDiagnosticReportS3Client(
            @Value("${app.diagnostic.report-storage.supabase-s3.endpoint}") URI endpoint,
            @Value("${app.diagnostic.report-storage.supabase-s3.region}") String region,
            @Value("${app.diagnostic.report-storage.supabase-s3.access-key}") String accessKey,
            @Value("${app.diagnostic.report-storage.supabase-s3.secret-key}") String secretKey) {
        String host = endpoint.getHost();
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || host == null
                || !host.toLowerCase(java.util.Locale.ROOT).endsWith(".storage.supabase.co")
                || !"/storage/v1/s3".equals(endpoint.getPath())
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || accessKey.isBlank() || secretKey.isBlank() || region.isBlank()) {
            throw new IllegalArgumentException("Invalid hosted Supabase Storage S3 configuration");
        }
        return S3Client.builder()
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
}
