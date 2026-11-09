package fu.fa26.sep490.g58.otcms_be.billing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

@Configuration
@EnableConfigurationProperties(PayOsProperties.class)
public class PayOsConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.payment.payos", name = "enabled", havingValue = "true")
    PayOS payOS(PayOsProperties properties) {
        if (blank(properties.clientId()) || blank(properties.apiKey()) || blank(properties.checksumKey())) {
            throw new IllegalStateException("PayOS credentials must be configured when PayOS is enabled");
        }
        return new PayOS(clientOptions(properties).build());
    }

    /**
     * The SDK's own defaults are 60s per call and 2 retries; billing calls PayOS from settle/reconcile/createLink, so
     * keep the worst case at one {@code timeoutMs} attempt. A timeout surfaces as an ordinary gateway error, which
     * settle treats as "unknown" (sweeper defers) and createLink flags for reconciliation.
     */
    static ClientOptions.ClientOptionsBuilder clientOptions(PayOsProperties properties) {
        return ClientOptions.builder()
                .clientId(properties.clientId()).apiKey(properties.apiKey()).checksumKey(properties.checksumKey())
                .timeoutMs(properties.timeoutMs()).maxRetries(0);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
