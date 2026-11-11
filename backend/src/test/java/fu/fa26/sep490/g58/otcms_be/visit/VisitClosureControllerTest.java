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
import fu.fa26.sep490.g58.otcms_be.visit.controller.WalkInVisitLifecycleController;
import fu.fa26.sep490.g58.otcms_be.visit.exception.*;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitProgressResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
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

@WebMvcTest(WalkInVisitLifecycleController.class)
@Import({SecurityConfig.class, VisitApiExceptionHandler.class, CorrelationIdFilter.class, VisitClosureControllerTest.Fixture.class})
class VisitClosureControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean WalkInVisitLifecycleService reads;
    @MockitoBean AppointmentBookingProperties booking;
    @MockitoBean Clock clock;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final UUID account = UUID.randomUUID(), visit = UUID.randomUUID();
    String path() { return "/api/v1/visits/" + visit + "/closure"; }
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @Test void doctorUsesAuthenticatedIdentity() throws Exception {
        mvc.perform(post(path()).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isNoContent());
        verify(reads).close(eq(visit), eq(account), eq(AccountRole.DOCTOR), anyString());
    }
    @Test void receptionistPatientAndOtherActorsDenied() throws Exception {
        for (var role : List.of(AccountRole.PATIENT, AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.OWNER, AccountRole.IMAGING_TECHNICIAN)) {
            mvc.perform(post(path()).with(actor(role)).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reads);
    }
    @Test void anonymousDenied() throws Exception {
        mvc.perform(post(path()).with(csrf())).andExpect(status().isUnauthorized()); verifyNoInteractions(reads);
    }
    @Test void inactiveDoctorDenialPreservesErrorContract() throws Exception {
        doThrow(new VisitOperationException("ACCESS_DENIED")).when(reads).close(eq(visit), eq(account), eq(AccountRole.DOCTOR), anyString());
        mvc.perform(post(path()).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void incompleteClinicalWorkReturnsConflict() throws Exception {
        doThrow(new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE")).when(reads).close(eq(visit), eq(account), eq(AccountRole.DOCTOR), anyString());
        mvc.perform(post(path()).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isConflict());
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
