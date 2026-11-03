package fu.fa26.sep490.g58.otcms_be.activitylog;
import fu.fa26.sep490.g58.otcms_be.activitylog.repository.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.exception.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.service.impl.*;
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
import fu.fa26.sep490.g58.otcms_be.activitylog.controller.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.service.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogEntry;
import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogPage;
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
@WebMvcTest(ActivityLogController.class)
@Import({
    fu.fa26.sep490.g58.otcms_be.content.HttpContractCapture.class,SecurityConfig.class, ActivityLogControllerTest.Fixture.class
})
class ActivityLogControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ActivityLogService query;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private AccountAccessService access;
    @MockitoBean private AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean private PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean private ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    private final String base = "/api/v1/admin/activity-logs";
    @Test void readsAreRestrictedToAdminAndOwnerForBothListAndDetail() throws Exception {
        for (String path : List.of(base, base + "/room:11111111-1111-1111-1111-111111111111")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : List.of("PATIENT", "RECEPTIONIST", "DOCTOR", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN"))
            mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(query);
    }
    @Test void authorizedResponsesHaveOnlySafeFieldsAndViewAction() throws Exception {
        var entry = new ActivityLogEntry("room:11111111-1111-1111-1111-111111111111", java.time.Instant.now(),
        "admin", "ADMIN", "CREATE_ROOM", null, "ROOM", "P.101", null, "P.101", null);
        when(query.list(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(new ActivityLogPage(List.of(entry), 0, 20, 1, 1));
        when(query.detail(entry.id())).thenReturn(entry);
        for (String role : List.of("ADMIN", "OWNER")) {
            mvc.perform(get(base).with(user("reader").roles(role))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].allowedActions[0]").value("view"))
            .andExpect(jsonPath("$.items[0].before_state").doesNotExist())
            .andExpect(jsonPath("$.items[0].after_state").doesNotExist())
            .andExpect(jsonPath("$.items[0].recordCode").doesNotExist());
            mvc.perform(get(base + "/" + entry.id()).with(user("reader").roles(role))).andExpect(status().isOk())
            .andExpect(jsonPath("$.recordCode").value("P.101"))
            .andExpect(jsonPath("$.payload").doesNotExist());
        }
    }
    @Test void invalidCriteriaAndMissingRowsUseDirectMessageCodes() throws Exception {
        when(query.list(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
        .thenThrow(new ActivityLogOperationException("MSG-54"));
        mvc.perform(get(base).param("from", "2026-11-07").param("to", "2026-11-01").with(user("admin").roles("ADMIN")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-54"));
        mvc.perform(get(base).param("page", "wrong").with(user("admin").roles("ADMIN")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-54"));
        when(query.detail(anyString())).thenThrow(new ActivityLogOperationException("MSG-50"));
        mvc.perform(get(base + "/unknown").with(user("admin").roles("ADMIN")))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));
    }
    @TestConfiguration
    @EnableWebSecurity
    @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture {
    }
}
