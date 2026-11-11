package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.s3;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Local synthetic-data storage only; production storage requires a reviewed binding. */
@Configuration(proxyBeanMethods = false)
@Profile("!prod & !supabase")
@ConditionalOnProperty(name = "app.diagnostic.report-storage.provider", havingValue = "s3-local")
public class LocalS3ReportConfiguration {
    @Bean(destroyMethod = "close")
    S3Client diagnosticReportS3Client(
            @Value("${app.diagnostic.report-storage.s3.endpoint}") URI endpoint,
            @Value("${app.diagnostic.report-storage.s3.region}") String region,
            @Value("${app.diagnostic.report-storage.s3.access-key}") String accessKey,
            @Value("${app.diagnostic.report-storage.s3.secret-key}") String secretKey) {
        String host = endpoint.getHost();
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) || host == null
                || !(host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")
                    || host.equals("::1")) || endpoint.getUserInfo() != null
                || accessKey.isBlank() || secretKey.isBlank() || region.isBlank()) {
            throw new IllegalArgumentException("Invalid local report storage configuration");
        }
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(10))
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .build())
                .build();
    }
}
