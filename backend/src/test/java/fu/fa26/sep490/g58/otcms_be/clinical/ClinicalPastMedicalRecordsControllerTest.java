package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalPastMedicalRecordsController;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PastMedicalRecordsDtos.HistoryResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPastMedicalRecordsService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.*;
import fu.fa26.sep490.g58.otcms_be.config.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

@WebMvcTest(ClinicalPastMedicalRecordsController.class)
@Import({SecurityConfig.class, ClinicalApiExceptionHandler.class, ClinicalDoctorIdentityResolver.class,
        CorrelationIdFilter.class, ClinicalPastMedicalRecordsControllerTest.Fixture.class})
class ClinicalPastMedicalRecordsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClinicalPastMedicalRecordsService history;
    @MockitoBean DoctorProfileIdentityQueryService doctors;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    UUID account = UUID.randomUUID(), doctor = UUID.randomUUID(), visit = UUID.randomUUID(), patient = UUID.randomUUID();
    String path() { return "/api/v1/visits/" + visit + "/past-medical-records"; }
    @BeforeEach void setup() {
        when(doctors.findDoctorProfileId(account)).thenReturn(Optional.of(doctor));
        when(history.getPastMedicalRecords(visit, doctor, account, 0, 20)).thenReturn(new HistoryResponse(visit, patient, List.of(), 0, 20, 0));
    }
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @Test void anonymousIsUnauthorized() throws Exception { mvc.perform(get(path())).andExpect(status().isUnauthorized()); verifyNoInteractions(history, doctors); }
    @Test void everyNonDoctorRoleIsForbidden() throws Exception {
        for (var role : AccountRole.values()) if (role != AccountRole.DOCTOR) {
            mvc.perform(get(path()).with(actor(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(history, doctors);
    }
    @Test void doctorWithoutProfileIsForbidden() throws Exception {
        when(doctors.findDoctorProfileId(account)).thenReturn(Optional.empty());
        mvc.perform(get(path()).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MSG-40"));
        verifyNoInteractions(history);
    }
    @Test void exactEmptyResponseNoStoreAndCorrelationAndServerIdentity() throws Exception {
        String correlation = UUID.randomUUID().toString();
        mvc.perform(get(path()).with(actor(AccountRole.DOCTOR)).header("X-Correlation-ID", correlation)
                .param("patientProfileId", UUID.randomUUID().toString()).param("doctorProfileId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Correlation-ID", correlation))
                .andExpect(content().json("""
                        {"currentVisitId":"%s","patientProfileId":"%s","items":[],"page":0,"size":20,"total":0}
                        """.formatted(visit, patient)))
                .andExpect(jsonPath("$.roomQueue").doesNotExist()).andExpect(jsonPath("$.laboratory").doesNotExist());
        verify(history).getPastMedicalRecords(visit, doctor, account, 0, 20);
    }
    @ParameterizedTest @CsvSource({"MSG-39,400", "MSG-40,403", "MSG-41,404", "MSG-42,409", "DEPENDENCY_UNAVAILABLE,503"})
    void domainFailuresUseExistingEnvelope(String code, int status) throws Exception {
        when(history.getPastMedicalRecords(any(), any(), any(), anyInt(), anyInt())).thenThrow(new ClinicalOperationException(code));
        mvc.perform(get(path()).with(actor(AccountRole.DOCTOR))).andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void invalidUuidAndPageFormatAreTransportValidationErrors() throws Exception {
        mvc.perform(get("/api/v1/visits/invalid/past-medical-records").with(actor(AccountRole.DOCTOR))).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("page", "invalid").with(actor(AccountRole.DOCTOR))).andExpect(status().isBadRequest());
        verifyNoInteractions(history);
    }
    @Test void paginationIsPassedWithoutChildPagination() throws Exception {
        when(history.getPastMedicalRecords(visit, doctor, account, 2, 5)).thenReturn(new HistoryResponse(visit, patient, List.of(), 2, 5, 7));
        mvc.perform(get(path()).param("page", "2").param("size", "5").with(actor(AccountRole.DOCTOR)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(7)).andExpect(jsonPath("$.page").value(2));
        verify(history).getPastMedicalRecords(visit, doctor, account, 2, 5);
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
