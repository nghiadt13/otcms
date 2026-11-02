package fu.fa26.sep490.g58.otcms_be.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGate;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({AppointmentBookingProperties.class, ProductionReadinessProperties.class})
public class AppointmentBookingConfiguration {
    @Bean
    ProductionReadinessGate productionReadinessGate(ProductionReadinessProperties properties) {
        return ProductionReadinessGate.load(properties);
    }

    /** Application business time is an absolute UTC instant; tests may provide a fixed Clock bean. */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock businessClock() {
        return Clock.systemUTC();
    }
}
