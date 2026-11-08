package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.PatientPrescriptionController;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.PatientPrescriptionQueryService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.*;
import fu.fa26.sep490.g58.otcms_be.config.*;
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

@WebMvcTest(PatientPrescriptionController.class)
@Import({SecurityConfig.class, ClinicalApiExceptionHandler.class, CorrelationIdFilter.class, PatientPrescriptionControllerTest.Fixture.class})
class PatientPrescriptionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PatientPrescriptionQueryService prescriptions;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final UUID account = UUID.randomUUID();
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @Test void anonymousAndOtherRolesDenied() throws Exception {
        mvc.perform(get("/api/v1/prescriptions")).andExpect(status().isUnauthorized());
        for (var role : List.of(AccountRole.DOCTOR, AccountRole.RECEPTIONIST, AccountRole.ADMIN)) {
            mvc.perform(get("/api/v1/prescriptions").with(actor(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(prescriptions);
    }
    @Test void patientPageContractAndPrivacyHeaders() throws Exception {
        when(prescriptions.list(any(), isNull(), eq(0), eq(20))).thenReturn(
                new PatientPrescriptionQueryService.PrescriptionPage(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/prescriptions").with(actor(AccountRole.PATIENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalItems").value(0)).andExpect(header().string("Cache-Control", "no-store"));
        verify(prescriptions).list(argThat(a -> a.accountId().equals(account)), isNull(), eq(0), eq(20));
    }
    @Test void invalidUuidUsesExistingErrorEnvelope() throws Exception {
        mvc.perform(get("/api/v1/prescriptions/not-a-uuid").with(actor(AccountRole.PATIENT)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(prescriptions);
    }
    @Test void authorizationAndUnavailableErrorsPreserved() throws Exception {
        UUID id = UUID.randomUUID();
        when(prescriptions.detail(any(), eq(id))).thenThrow(new ClinicalOperationException("MSG-40"));
        mvc.perform(get("/api/v1/prescriptions/" + id).with(actor(AccountRole.PATIENT)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MSG-40"));
        when(prescriptions.detail(any(), eq(id))).thenThrow(new ClinicalOperationException("MSG-41"));
        mvc.perform(get("/api/v1/prescriptions/" + id).with(actor(AccountRole.PATIENT)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-41"));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
