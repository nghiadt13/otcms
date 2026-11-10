package fu.fa26.sep490.g58.otcms_be.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Unconditional switch for @Scheduled jobs (appointment payment-hold sweeper, imaging jobs). Kept apart from any
 * feature configuration so a feature flag can never silence another feature's jobs. Repeating @EnableScheduling on
 * another configuration is harmless: Spring registers a single scheduling post-processor.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
