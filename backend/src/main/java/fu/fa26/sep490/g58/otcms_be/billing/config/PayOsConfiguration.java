package fu.fa26.sep490.g58.otcms_be.billing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
@EnableConfigurationProperties(PayOsProperties.class)
public class PayOsConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.payment.payos", name = "enabled", havingValue = "true")
    PayOS payOS(PayOsProperties properties) {
        if (blank(properties.clientId()) || blank(properties.apiKey()) || blank(properties.checksumKey())) {
            throw new IllegalStateException("PayOS credentials must be configured when PayOS is enabled");
        }
        return new PayOS(properties.clientId(), properties.apiKey(), properties.checksumKey());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
