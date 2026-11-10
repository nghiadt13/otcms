package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.pdf;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportContentValidator;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav.ClamAvInstreamClient;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav.ClamAvScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Local synthetic-data binding. External scanner hosts are rejected by design. */
@Configuration(proxyBeanMethods = false)
@Profile("!prod")
@ConditionalOnProperty(name = "app.diagnostic.report-validation.provider", havingValue = "clamav-local", matchIfMissing = true)
public class LocalReportValidationConfiguration {
    @Bean
    ClamAvScanner diagnosticReportClamAvScanner(
            @Value("${app.diagnostic.report-validation.clamav.host:127.0.0.1}") String host,
            @Value("${app.diagnostic.report-validation.clamav.port:3310}") int port,
            @Value("${app.diagnostic.report-validation.clamav.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${app.diagnostic.report-validation.clamav.read-timeout-ms:15000}") int readTimeoutMs) {
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!loopback || port < 1 || port > 65535 || connectTimeoutMs < 1 || readTimeoutMs < 1) {
            throw new IllegalArgumentException("Invalid local ClamAV configuration");
        }
        return new ClamAvInstreamClient(host, port, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    ReportContentValidator diagnosticReportContentValidator(ClamAvScanner scanner) {
        return new PdfBoxClamAvReportValidator(scanner);
    }
}
