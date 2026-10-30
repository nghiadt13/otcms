package fu.fa26.sep490.g58.otcms_be.staff;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import fu.fa26.sep490.g58.otcms_be.staff.controller.AdminDoctorProfileController;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.service.AdminDoctorProfileService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminDoctorProfileController.class)
@Import({SecurityConfig.class, AdminDoctorProfileControllerTest.Fixture.class})
class AdminDoctorProfileControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AdminDoctorProfileService service;
    @MockitoBean private DoctorProfileMapper mapper;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private AccountAccessService access;
    @MockitoBean private AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean private PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean private ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    private final String base = "/api/v1/admin/doctor-profiles";
    private final UUID id = UUID.randomUUID();

    @Test void nonAdminsCannotReadInternalDirectoryAccountsOrTimeOffs() throws Exception {
        for (String path : List.of(base, base + "/eligible-accounts", base + "/" + id + "/time-offs?from=2026-10-28T00:00:00Z&to=2026-10-30T00:00:00Z")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : List.of("OWNER", "DOCTOR", "PATIENT", "RECEPTIONIST"))
                mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(put(base + "/" + id).with(user("other").roles("DOCTOR")).with(csrf())
                .contentType("application/json").content("{\"fullName\":\"Name\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void adminGetsSafeEligibleAccountProjection() throws Exception {
        when(service.eligibleAccounts()).thenReturn(List.of(new fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService.DoctorAccountOption(id, "doctor", "Doctor")));
        mvc.perform(get(base + "/eligible-accounts").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].accountId").value(id.toString()))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
    }

    @Test void blankNameAndMissingCsrfNeverUpdateProfile() throws Exception {
        mvc.perform(put(base + "/" + id).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"fullName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(base + "/" + id).with(user("admin").roles("ADMIN")).contentType("application/json").content("{\"fullName\":\"Doctor\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).update(any(), any());
    }

    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
