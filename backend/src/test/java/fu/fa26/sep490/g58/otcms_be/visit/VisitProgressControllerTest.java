package fu.fa26.sep490.g58.otcms_be.visit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.*;
import fu.fa26.sep490.g58.otcms_be.config.*;
import fu.fa26.sep490.g58.otcms_be.visit.controller.VisitReceptionReadController;
import fu.fa26.sep490.g58.otcms_be.visit.exception.*;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitProgressResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReceptionReadService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(VisitReceptionReadController.class)
@Import({SecurityConfig.class, VisitApiExceptionHandler.class, CorrelationIdFilter.class, VisitProgressControllerTest.Fixture.class})
class VisitProgressControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VisitReceptionReadService reads;
    @MockitoBean AppointmentBookingProperties booking;
    @MockitoBean Clock clock;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final UUID account = UUID.randomUUID(), visit = UUID.randomUUID();
    String path() { return "/api/v1/visits/" + visit + "/progress"; }
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @Test void supportedActorsUseAuthenticatedIdentityAndReceivePrivateOperationalProjection() throws Exception {
        when(reads.getProgress(any(), eq(visit))).thenReturn(new VisitProgressResponse(visit, "WALK_IN", "OPEN", "EXAMINATION", null, null, "ROOM-1"));
        for (var role : List.of(AccountRole.PATIENT, AccountRole.RECEPTIONIST, AccountRole.DOCTOR)) {
            mvc.perform(get(path()).with(actor(role))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.visitId").value(visit.toString())).andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(header().string("Cache-Control", "no-store"));
            verify(reads).getProgress(argThat(a -> a.accountId().equals(account) && a.accountRole() == role), eq(visit));
        }
    }
    @Test void anonymousAndUnsupportedActorsDenied() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        for (var role : List.of(AccountRole.ADMIN, AccountRole.OWNER, AccountRole.IMAGING_TECHNICIAN)) {
            mvc.perform(get(path()).with(actor(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reads);
    }
    @Test void currentCareDenialKeepsExistingVisitErrorContract() throws Exception {
        when(reads.getProgress(any(), eq(visit))).thenThrow(new VisitOperationException("ACCESS_DENIED"));
        mvc.perform(get(path()).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
