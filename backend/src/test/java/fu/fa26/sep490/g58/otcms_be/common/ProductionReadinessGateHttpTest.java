package fu.fa26.sep490.g58.otcms_be.common;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.api.AccessDeniedExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.common.api.ProductionReadinessStatusController;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGate;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.ProductionReadinessProperties;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(ProductionReadinessStatusController.class)
@Import({SecurityConfig.class, AccessDeniedExceptionHandler.class, ProductionReadinessGateHttpTest.Fixture.class})
class ProductionReadinessGateHttpTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductionReadinessGate gate;
    @MockitoBean
    private JwtTokenService tokens;
    @MockitoBean
    private AccountAccessService access;
    @MockitoBean
    private AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean
    private PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean
    private ProductionReadinessGateInterceptor productionReadinessGateInterceptor;

    @Test
    void statusEndpointReturnsReadinessForAdminAndEnforces401And403() throws Exception {
        mvc.perform(get("/api/v1/system/production-readiness"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/system/production-readiness")
                        .with(authentication(new AnonymousAuthenticationToken("key", "anon",
                                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")))))
                .andExpect(status().isUnauthorized());

        var patientAuth = new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccount(UUID.randomUUID(), AccountRole.PATIENT, UUID.randomUUID()),
                null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
        mvc.perform(get("/api/v1/system/production-readiness")
                        .requestAttr(CorrelationIdFilter.ATTRIBUTE, "corr-403")
                        .with(authentication(patientAuth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.error.correlationId").value("corr-403"));

        when(gate.isClosed()).thenReturn(false);
        mvc.perform(get("/api/v1/system/production-readiness").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandEndpointsEnabled").value(true));

        when(gate.isClosed()).thenReturn(true);
        mvc.perform(get("/api/v1/system/production-readiness").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandEndpointsEnabled").value(false));
    }

    @Test
    void gateInterceptorBlocksProtectedCommandsWith503WhenClosedAndAllowsWhenOpen() throws Exception {
        ProductionReadinessGate closedGate = ProductionReadinessGate.load(null);
        var closedInterceptor = new ProductionReadinessGateInterceptor(closedGate, new ObjectMapper());
        MockMvc closedMvc = MockMvcBuilders.standaloneSetup(new DummyCommandController())
                .addInterceptors(closedInterceptor)
                .build();

        closedMvc.perform(post("/api/v1/appointments")
                        .requestAttr(CorrelationIdFilter.ATTRIBUTE, "corr-503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_READINESS_GATE_CLOSED"))
                .andExpect(jsonPath("$.error.correlationId").value("corr-503"));

        closedMvc.perform(post("/api/v1/appointments/pending-hold/cancel"))
                .andExpect(status().isOk());

        ProductionReadinessProperties openProps = new ProductionReadinessProperties();
        openProps.setRequired(false);
        var openInterceptor = new ProductionReadinessGateInterceptor(ProductionReadinessGate.load(openProps), new ObjectMapper());
        MockMvc openMvc = MockMvcBuilders.standaloneSetup(new DummyCommandController())
                .addInterceptors(openInterceptor)
                .build();

        openMvc.perform(post("/api/v1/appointments"))
                .andExpect(status().isOk());
    }

    @RestController
    static class DummyCommandController {
        @PostMapping("/api/v1/appointments")
        String createAppointment() { return "ok"; }

        @PostMapping("/api/v1/appointments/pending-hold/cancel")
        String releaseHold() { return "released"; }
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
