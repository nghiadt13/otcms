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
import fu.fa26.sep490.g58.otcms_be.clinical.controller.VisitAmendmentController;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet.ResultAmendmentService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(VisitAmendmentController.class)
@Import({SecurityConfig.class, ClinicalApiExceptionHandler.class, CorrelationIdFilter.class, VisitAmendmentControllerTest.Fixture.class})
class VisitAmendmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    @MockitoBean ResultAmendmentService amendments;

    private final UUID visit = UUID.randomUUID(), document = UUID.randomUUID();
    private static final String BODY = "{\"advice\":{\"homeCareAdvice\":\"Chườm lạnh\"},\"reason\":\"Sửa lời dặn\"}";
    private String path() { return "/api/v1/visits/" + visit + "/amendments"; }

    @Test void onlyDoctorsMayAmendAndSuccessReturnsNewVersion() throws Exception {
        when(amendments.amend(any(), eq(visit), any())).thenReturn(new ResultAmendmentService.Amended(document, 2));
        mvc.perform(post(path()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        for (AccountRole role : List.of(AccountRole.PATIENT, AccountRole.RECEPTIONIST, AccountRole.ADMIN)) {
            mvc.perform(post(path()).with(csrf()).with(actor(role)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post(path()).with(csrf()).with(actor(AccountRole.DOCTOR)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.documentId").value(document.toString()))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(amendments, times(1)).amend(any(), eq(visit), any());
    }

    @Test void businessErrorsKeepTheirMessageCodeAndStatus() throws Exception {
        for (var expected : List.of(new Object[] {"MSG-40", 403}, new Object[] {"MSG-42", 409}, new Object[] {"MSG-39", 400}, new Object[] {"MSG-41", 404})) {
            when(amendments.amend(any(), eq(visit), any())).thenThrow(new ClinicalOperationException((String) expected[0]));
            mvc.perform(post(path()).with(csrf()).with(actor(AccountRole.DOCTOR)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().is((Integer) expected[1])).andExpect(jsonPath("$.error.code").value(expected[0]));
        }
    }

    @Test void invalidPrescriptionItemIsRejectedAtTheBoundary() throws Exception {
        String body = "{\"prescription\":{\"items\":[{\"medicine\":\"\",\"dose\":\"1\",\"frequency\":\"1\",\"usage\":\"1\"}]},\"reason\":\"x\"}";
        mvc.perform(post(path()).with(csrf()).with(actor(AccountRole.DOCTOR)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(amendments);
    }

    @Test void currentValuesAreForDoctorsOnly() throws Exception {
        when(amendments.current(any(), eq(visit))).thenReturn(new fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AmendableResultResponse(null, null, List.of(), 3L));
        mvc.perform(get(path() + "/current")).andExpect(status().isUnauthorized());
        mvc.perform(get(path() + "/current").with(actor(AccountRole.PATIENT))).andExpect(status().isForbidden());
        mvc.perform(get(path() + "/current").with(actor(AccountRole.DOCTOR))).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultSheetVersion").value(3)).andExpect(header().string("Cache-Control", "no-store"));
    }

    private RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(UUID.randomUUID(), role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
