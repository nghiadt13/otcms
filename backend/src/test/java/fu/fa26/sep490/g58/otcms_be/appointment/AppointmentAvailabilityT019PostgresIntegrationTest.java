package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(AppointmentAvailabilityT019PostgresIntegrationTest.FixedClockConfiguration.class)
class AppointmentAvailabilityT019PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final LocalDate AVAILABLE_DATE = LocalDate.of(2026, 10, 15);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID serviceId;
    private List<UUID> doctorIds;
    private List<UUID> accountIds;
    private UUID unassignedServiceId;
    private UUID inactiveServiceId;
    private UUID staffOnlyServiceId;

    @BeforeEach
    void setUp() {
        serviceId = UUID.randomUUID();
        unassignedServiceId = UUID.randomUUID();
        inactiveServiceId = UUID.randomUUID();
        staffOnlyServiceId = UUID.randomUUID();
        doctorIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        accountIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
        insertService(serviceId, true, true, "T019 available service");
        insertService(unassignedServiceId, true, true, "T019 unassigned service");
        insertService(inactiveServiceId, true, false, "T019 inactive service");
        insertService(staffOnlyServiceId, false, true, "T019 staff-only service");
        for (int index = 0; index < doctorIds.size(); index++) {
            UUID doctorId = doctorIds.get(index);
            UUID accountId = accountIds.get(index);
            jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                            + "values (?, ?, 'DOCTOR', 'ACTIVE', ?, ?)", accountId,
                    "t019-" + accountId + "@example.test", Timestamp.from(createdAt), Timestamp.from(createdAt));
            boolean visible = index != 1;
            boolean accepting = index != 2;
            jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                            + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                            + "values (?, ?, ?, '', 'private-contact', '', '', '', '', ?, ?, true)",
                    doctorId, accountId, "T019 Doctor " + index, visible, accepting);
            jdbc.update("insert into doctor_shifts (id, doctor_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                    UUID.randomUUID(), doctorId, Timestamp.from(shiftStart()), Timestamp.from(shiftEnd()));
            if (index < 3) {
                jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                        doctorId, serviceId);
            }
        }
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorIds.get(0), staffOnlyServiceId);
    }

    @AfterEach
    void cleanUp() {
        if (serviceId == null) return;
        jdbc.update("delete from doctor_services where service_id in (?, ?, ?, ?)",
                serviceId, unassignedServiceId, inactiveServiceId, staffOnlyServiceId);
        jdbc.update("delete from doctor_shifts where doctor_profile_id in (?, ?, ?, ?)",
                doctorIds.get(0), doctorIds.get(1), doctorIds.get(2), doctorIds.get(3));
        jdbc.update("delete from doctor_profiles where id in (?, ?, ?, ?)",
                doctorIds.get(0), doctorIds.get(1), doctorIds.get(2), doctorIds.get(3));
        jdbc.update("delete from accounts where id in (?, ?, ?, ?)",
                accountIds.get(0), accountIds.get(1), accountIds.get(2), accountIds.get(3));
        jdbc.update("delete from clinic_services where id in (?, ?, ?, ?)",
                serviceId, unassignedServiceId, inactiveServiceId, staffOnlyServiceId);
    }

    @Test
    void publicAvailabilityFiltersProviderVisibilityAcceptanceAssignmentAndNoticeWithoutLeakingPrivateData() throws Exception {
        mvc.perform(get("/api/v1/appointments/availability")
                        .param("serviceId", serviceId.toString())
                        .param("from", AVAILABLE_DATE.toString()).param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[*].doctorId", hasItem(doctorIds.get(0).toString())))
                .andExpect(jsonPath("$.slots[*].doctorId", not(hasItem(doctorIds.get(1).toString()))))
                .andExpect(jsonPath("$.slots[*].doctorId", not(hasItem(doctorIds.get(2).toString()))))
                .andExpect(jsonPath("$.slots[*].doctorId", not(hasItem(doctorIds.get(3).toString()))))
                .andExpect(jsonPath("$.slots[*].doctorDisplayName", hasItem("T019 Doctor 0")))
                .andExpect(jsonPath("$.slots[*].startTime", hasItem("2026-10-15T07:00:00Z")))
                .andExpect(jsonPath("$..workContact").doesNotExist())
                .andExpect(jsonPath("$..patientProfileId").doesNotExist())
                .andExpect(jsonPath("$..unavailabilityReason").doesNotExist());
    }

    @Test
    void staffAndAdminCanInspectHiddenAcceptingProvidersButNotNonAcceptingOrUnassignedOnes() throws Exception {
        mvc.perform(get("/api/v1/staff/appointments/availability").with(actor(AccountType.RECEPTIONIST))
                        .param("serviceId", serviceId.toString())
                        .param("from", AVAILABLE_DATE.toString()).param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[*].doctorId", hasItem(doctorIds.get(0).toString())))
                .andExpect(jsonPath("$.slots[*].doctorId", hasItem(doctorIds.get(1).toString())))
                .andExpect(jsonPath("$.slots[*].doctorId", not(hasItem(doctorIds.get(2).toString()))))
                .andExpect(jsonPath("$.slots[*].doctorId", not(hasItem(doctorIds.get(3).toString()))));

        mvc.perform(get("/api/v1/staff/appointments/availability").with(actor(AccountType.ADMIN))
                        .param("serviceId", serviceId.toString())
                        .param("from", AVAILABLE_DATE.toString()).param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[*].doctorId", hasItem(doctorIds.get(1).toString())));
    }

    @Test
    void doctorMayInspectAnotherEligibleProviderForFollowUp() throws Exception {
        mvc.perform(get("/api/v1/staff/appointments/availability").with(actor(AccountType.DOCTOR))
                        .param("serviceId", serviceId.toString())
                        .param("doctorId", doctorIds.get(1).toString())
                        .param("from", AVAILABLE_DATE.toString()).param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[*].doctorId", hasItem(doctorIds.get(1).toString())))
                .andExpect(jsonPath("$.slots[*].doctorId", not(hasItem(doctorIds.get(0).toString()))));
    }

    @Test
    void patientCannotUseStaffAvailabilityAndAdminHasReadOnlyEndpoint() throws Exception {
        mvc.perform(get("/api/v1/staff/appointments/availability").with(actor(AccountType.PATIENT))
                        .param("serviceId", serviceId.toString())
                        .param("from", AVAILABLE_DATE.toString()).param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ineligibleServiceDoctorAndInvalidWindowsUseStableErrorEnvelope() throws Exception {
        mvc.perform(get("/api/v1/appointments/availability")
                        .param("serviceId", staffOnlyServiceId.toString())
                        .param("from", AVAILABLE_DATE.toString()).param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SERVICE_NOT_BOOKABLE"));

        mvc.perform(get("/api/v1/staff/appointments/availability").with(actor(AccountType.RECEPTIONIST))
                        .param("serviceId", serviceId.toString()).param("doctorId", doctorIds.get(2).toString())
                        .param("from", AVAILABLE_DATE.toString()).param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DOCTOR_NOT_ELIGIBLE"));

        mvc.perform(get("/api/v1/appointments/availability")
                        .param("serviceId", serviceId.toString()).param("from", "not-a-date").param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void emptyAvailabilityIsSuccessfulAndOutsideHorizonIsRejected() throws Exception {
        mvc.perform(get("/api/v1/staff/appointments/availability").with(actor(AccountType.RECEPTIONIST))
                        .param("serviceId", unassignedServiceId.toString())
                        .param("from", AVAILABLE_DATE.toString()).param("to", AVAILABLE_DATE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots").isEmpty());

        mvc.perform(get("/api/v1/appointments/availability")
                        .param("serviceId", serviceId.toString())
                        .param("from", "2026-10-13").param("to", "2026-10-13"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BOOKING_WINDOW_VIOLATION"));
    }

    private void insertService(UUID id, boolean onlineBookable, boolean active, String name) {
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                        + "values (?, ?, 30, 5, ?, ?)", id, name, onlineBookable, active);
    }

    private Instant shiftStart() {
        return AVAILABLE_DATE.atTime(13, 30).atZone(CLINIC_ZONE).toInstant();
    }

    private Instant shiftEnd() {
        return AVAILABLE_DATE.atTime(15, 0).atZone(CLINIC_ZONE).toInstant();
    }

    private RequestPostProcessor actor(AccountType type) {
        var principal = new AuthenticatedAccount(UUID.randomUUID(), fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Primary
        @Bean Clock availabilityTestClock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }
}
