package fu.fa26.sep490.g58.otcms_be.appointment.controller;

import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "appointment.booking.public-rate-limit.requests=2",
        "appointment.booking.public-rate-limit.window-seconds=10"
})
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(PublicRequestRateLimitT021PostgresIntegrationTest.FixedClockConfiguration.class)
class PublicRequestRateLimitT021PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final String CLIENT_IP = "198.51.100.41";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID serviceId;
    private String correlationId;

    @BeforeEach
    void setUp() {
        serviceId = UUID.randomUUID();
        correlationId = UUID.randomUUID().toString();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                        + "values (?, 'T021 rate-limit service', 30, 5, true, true)", serviceId);
    }

    @AfterEach
    void cleanUp() {
        if (serviceId != null) jdbc.update("delete from clinic_services where id = ?", serviceId);
    }

    @Test
    void limitsCatalogAndAvailabilityIndependentlyAndReturnsSafeCorrelatedRetryResponse() throws Exception {
        for (int requestNumber = 0; requestNumber < 2; requestNumber++) {
            mvc.perform(get("/api/v1/clinic-services").with(clientRequest()).header(CorrelationIdFilter.HEADER, correlationId))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/clinic-services").with(clientRequest()).header(CorrelationIdFilter.HEADER, correlationId))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, correlationId))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.correlationId").value(correlationId))
                .andExpect(jsonPath("$.error.message").value("Too many public requests"));

        String today = LocalDate.now(Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"))).toString();
        for (int requestNumber = 0; requestNumber < 2; requestNumber++) {
            mvc.perform(get("/api/v1/appointments/availability").with(clientRequest())
                            .param("serviceId", serviceId.toString()).param("from", today).param("to", today))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slots").isEmpty());
        }
        mvc.perform(get("/api/v1/appointments/availability").with(clientRequest())
                        .param("serviceId", serviceId.toString()).param("from", today).param("to", today))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));

        mvc.perform(get("/api/v1/clinic-services").with(clientRequest("198.51.100.43")))
                .andExpect(status().isOk());
    }

    @Test
    void permitsAnonymousHeadRequestsAndAppliesTheSamePublicLimit() throws Exception {
        mvc.perform(head("/api/v1/clinic-services").with(clientRequest("198.51.100.42")))
                .andExpect(status().isOk());
        mvc.perform(head("/api/v1/clinic-services").with(clientRequest("198.51.100.42")))
                .andExpect(status().isOk());
        mvc.perform(head("/api/v1/clinic-services").with(clientRequest("198.51.100.42")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10"));

        String today = LocalDate.now(Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"))).toString();
        mvc.perform(head("/api/v1/appointments/availability").with(clientRequest("198.51.100.42"))
                        .param("serviceId", serviceId.toString()).param("from", today).param("to", today))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor clientRequest() { return clientRequest(CLIENT_IP); }

    private RequestPostProcessor clientRequest(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Primary
        @Bean Clock rateLimitTestClock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }
}
