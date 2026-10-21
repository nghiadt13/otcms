package fu.fa26.sep490.g58.otcms_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentBookingPropertiesTest {
    private static final String[] SPEC_DEFAULTS = {
            "appointment.booking.max-horizon-days=30",
            "appointment.booking.online-minimum-notice-minutes=120",
            "appointment.booking.patient-change-deadline-minutes=1440",
            "appointment.booking.check-in-early-minutes=30",
            "appointment.booking.check-in-late-grace-minutes=15",
            "appointment.booking.slot-interval-minutes=15",
            "appointment.booking.clinic-time-zone=Asia/Ho_Chi_Minh",
            "appointment.booking.public-rate-limit.requests=60",
            "appointment.booking.public-rate-limit.window-seconds=60"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AppointmentBookingConfiguration.class)
            .withPropertyValues(SPEC_DEFAULTS);
    private final ApplicationContextRunner applicationConfigRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(AppointmentBookingConfiguration.class);

    @Test
    void bindsTheSpecifiedPolicyAndProvidesUtcBusinessClock() {
        applicationConfigRunner.run(context -> {
            assertNull(context.getStartupFailure());
            AppointmentBookingProperties properties = context.getBean(AppointmentBookingProperties.class);
            assertEquals(30, properties.maxHorizonDays());
            assertEquals(120, properties.onlineMinimumNoticeMinutes());
            assertEquals(1440, properties.patientChangeDeadlineMinutes());
            assertEquals(30, properties.checkInEarlyMinutes());
            assertEquals(15, properties.checkInLateGraceMinutes());
            assertEquals(15, properties.slotIntervalMinutes());
            assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), properties.clinicTimeZone());
            assertEquals(new AppointmentBookingProperties.PublicRateLimit(60, 60), properties.publicRateLimit());
            assertEquals(ZoneOffset.UTC, context.getBean(Clock.class).getZone());
        });
    }

    @Test
    void acceptsOverridesIncludingZeroForNonNegativePoliciesAndUsesInjectableClock() {
        Instant fixedInstant = Instant.parse("2026-10-14T09:30:00Z");
        contextRunner.withUserConfiguration(FixedClockConfiguration.class)
                .withPropertyValues(
                        "appointment.booking.max-horizon-days=45",
                        "appointment.booking.online-minimum-notice-minutes=0",
                        "appointment.booking.patient-change-deadline-minutes=0",
                        "appointment.booking.check-in-early-minutes=0",
                        "appointment.booking.check-in-late-grace-minutes=0",
                        "appointment.booking.slot-interval-minutes=20",
                        "appointment.booking.clinic-time-zone=Asia/Bangkok",
                        "appointment.booking.public-rate-limit.requests=12",
                        "appointment.booking.public-rate-limit.window-seconds=5")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    AppointmentBookingProperties properties = context.getBean(AppointmentBookingProperties.class);
                    assertEquals(45, properties.maxHorizonDays());
                    assertEquals(0, properties.onlineMinimumNoticeMinutes());
                    assertEquals(0, properties.patientChangeDeadlineMinutes());
                    assertEquals(0, properties.checkInEarlyMinutes());
                    assertEquals(0, properties.checkInLateGraceMinutes());
                    assertEquals(20, properties.slotIntervalMinutes());
                    assertEquals(ZoneId.of("Asia/Bangkok"), properties.clinicTimeZone());
                    assertEquals(new AppointmentBookingProperties.PublicRateLimit(12, 5), properties.publicRateLimit());
                    assertEquals(fixedInstant, context.getBean(Clock.class).instant());
                });
    }

    @Test
    void rejectsInvalidConfigurationAtContextStartup() {
        assertStartupRejected("appointment.booking.max-horizon-days=0");
        assertStartupRejected("appointment.booking.online-minimum-notice-minutes=-1");
        assertStartupRejected("appointment.booking.patient-change-deadline-minutes=-1");
        assertStartupRejected("appointment.booking.check-in-early-minutes=-1");
        assertStartupRejected("appointment.booking.check-in-late-grace-minutes=-1");
        assertStartupRejected("appointment.booking.slot-interval-minutes=7");
        assertStartupRejected("appointment.booking.clinic-time-zone=Not/A_Zone");
        assertStartupRejected("appointment.booking.public-rate-limit.requests=0");
        assertStartupRejected("appointment.booking.public-rate-limit.window-seconds=0");
    }

    private void assertStartupRejected(String invalidProperty) {
        contextRunner.withPropertyValues(invalidProperty)
                .run(context -> assertNotNull(context.getStartupFailure(),
                        "Expected startup rejection for " + invalidProperty));
    }

    @Configuration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @org.springframework.context.annotation.Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-14T09:30:00Z"), ZoneId.of("UTC"));
        }
    }
}
