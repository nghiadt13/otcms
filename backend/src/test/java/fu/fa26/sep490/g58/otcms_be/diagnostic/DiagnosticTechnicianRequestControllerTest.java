package fu.fa26.sep490.g58.otcms_be.diagnostic;

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
import fu.fa26.sep490.g58.otcms_be.diagnostic.controller.DiagnosticTechnicianRequestController;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.*;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitProgressResponse;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticTechnicianRequestQueryService;
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

@WebMvcTest(DiagnosticTechnicianRequestController.class)
@Import({SecurityConfig.class, DiagnosticApiExceptionHandler.class, CorrelationIdFilter.class, DiagnosticTechnicianRequestControllerTest.Fixture.class})
class DiagnosticTechnicianRequestControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DiagnosticTechnicianRequestQueryService reads;
    @MockitoBean AppointmentBookingProperties booking;
    @MockitoBean Clock clock;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final UUID account = UUID.randomUUID(), visit = UUID.randomUUID();
    String path() { return "/api/v1/imaging-requests/" + visit + "/clinical-details"; }
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @Test void assignedTechnicianReceivesPrivateClinicalDetails() throws Exception {
        when(reads.getPendingRequest(any(), eq(visit))).thenReturn(new fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.TechnicianImagingRequestResponse(
                visit, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "MRI", "Knee", "Pain", "Instructions",
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus.PENDING));
        mvc.perform(get(path()).with(actor(AccountRole.IMAGING_TECHNICIAN))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.bodyArea").value("Knee"))
                .andExpect(jsonPath("$.instructions").value("Instructions")).andExpect(jsonPath("$.studyRef").doesNotExist());
        verify(reads).getPendingRequest(argThat(a -> a.accountId().equals(account) && a.role() == AccountRole.IMAGING_TECHNICIAN), eq(visit));
    }
    @Test void anonymousAndOtherRolesDenied() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        for (var role : List.of(AccountRole.PATIENT, AccountRole.DOCTOR, AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.OWNER)) {
            mvc.perform(get(path()).with(actor(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reads);
    }
    @Test void unassignedTechnicianReceivesUnavailableError() throws Exception {
        when(reads.getPendingRequest(any(), eq(visit))).thenThrow(new DiagnosticOperationException(
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        mvc.perform(get(path()).with(actor(AccountRole.IMAGING_TECHNICIAN))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_AVAILABLE"));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
