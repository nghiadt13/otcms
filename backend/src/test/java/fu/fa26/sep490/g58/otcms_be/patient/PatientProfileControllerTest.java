package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.patient.controller.PatientProfileController;
import fu.fa26.sep490.g58.otcms_be.patient.exception.*;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.PatientProfileMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientProfileServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientProfileController.class)
@Import({SecurityConfig.class, PatientProfileMapper.class, PatientProfileExceptionHandler.class, PatientProfileControllerTest.Fixture.class})
class PatientProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PatientProfileService profiles;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    static final String PROFILE_ID = "01234567-89ab-cdef-0123-456789abcdef";
    final UUID id = UUID.fromString(PROFILE_ID), actorId = UUID.randomUUID();
    static final String ROOT = "/api/v1/patient-profiles";
    static final String BODY = """
            {"fullName":"Nguyễn An","phone":"0912345678","phoneOwnerType":"PATIENT",
             "dateOfBirth":"2000-01-02","gender":"OTHER","address":"Address"}
            """;
    PatientProfile profile() {
        return new PatientProfile(id, "Nguyễn An", "+84912345678", LocalDate.of(2000, 1, 2), "OTHER",
                "PATIENT", "Address", null, null, false, 1);
    }
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(actorId, role, UUID.randomUUID()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "search" -> get(ROOT).param("query", id.toString());
            case "matches" -> get(ROOT + "/matches").param("phone", "0912345678").param("dateOfBirth", "2000-01-02");
            case "detail" -> get(ROOT + "/" + id);
            case "create" -> post(ROOT).with(csrf()).contentType("application/json").content(BODY);
            default -> put(ROOT + "/" + id).with(csrf()).contentType("application/json")
                    .content(BODY.strip().replace("}", ",\"version\":0}"));
        };
    }
    @ParameterizedTest @ValueSource(strings = {"search", "matches", "detail", "create", "update"})
    void eachEndpointEnforcesEveryRoleAndAnonymous(String operation) throws Exception {
        mvc.perform(request(operation)).andExpect(status().isUnauthorized());
        for (AccountRole role : AccountRole.values()) {
            if (role != AccountRole.RECEPTIONIST)
                mvc.perform(request(operation).with(actor(role))).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error.code").value("MSG-19"));
        }
        verifyNoInteractions(profiles);
        when(profiles.search(id.toString(), 0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));
        when(profiles.matches(any(), any(), any(), any())).thenReturn(List.of());
        when(profiles.detail(any(), any())).thenReturn(profile());
        when(profiles.create(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(profile());
        when(profiles.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(profile());
        mvc.perform(request(operation).with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(operation.equals("create") ? status().isCreated() : status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {PROFILE_ID, "01234567-89AB-CDEF-0123-456789ABCDEF", " \t" + PROFILE_ID + "  "})
    void receptionistSearchReturnsUuidMatchInExistingPageResponse(String query) throws Exception {
        var directory = useRealSearch();
        var patient = profile();
        when(directory.findProfile(id)).thenReturn(Optional.of(new ProfileRecord(
                id, null, patient.fullName(), patient.phoneCanonical(), "PATIENT", patient.dateOfBirth(), "OTHER")));

        mvc.perform(get(ROOT).param("query", query).with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].patientProfileId").value(PROFILE_ID))
                .andExpect(jsonPath("$.content[0].fullName").value(patient.fullName()))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get(ROOT).param("query", query).param("page", "1").param("size", "5")
                        .with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void receptionistSearchReturnsEmptyPageForUnknownOrMalformedUuid() throws Exception {
        var directory = useRealSearch();
        String malformed = "01234567-89ab-cdef-0123-456789abcdeg";
        var pageable = PageRequest.of(0, 20);
        when(directory.findProfile(id)).thenReturn(Optional.empty());
        when(directory.search(malformed, null, pageable)).thenReturn(Page.empty(pageable));

        for (String query : List.of(PROFILE_ID, malformed)) {
            mvc.perform(get(ROOT).param("query", query).with(actor(AccountRole.RECEPTIONIST)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    private PatientProfileDirectory useRealSearch() {
        var directory = mock(PatientProfileDirectory.class);
        var service = new PatientProfileServiceImpl(
                directory, mock(CoordinationGuardService.class), mock(ReceptionistAccountAccessService.class));
        when(profiles.search(anyString(), anyInt(), anyInt())).thenAnswer(
                invocation -> service.search(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        return directory;
    }

    @Test void detailAndSummaryHaveExactAdministrativeWhitelists() throws Exception {
        when(profiles.detail(actorId, id)).thenReturn(profile());
        var detail = mvc.perform(request("detail").with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.hasAccount").value(false)).andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(detail);
        var fields = new HashSet<String>(); json.propertyNames().forEach(fields::add);
        org.junit.jupiter.api.Assertions.assertEquals(Set.of("patientProfileId", "fullName", "phoneCanonical", "phoneOwnerType",
                "dateOfBirth", "gender", "address", "nationalId", "identityDocumentType", "hasAccount", "version"), fields);
        when(profiles.matches(any(), any(), any(), any())).thenReturn(List.of(profile()));
        var summary = mvc.perform(request("matches").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var summaryFields = new HashSet<String>(); json = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(summary).get("items").get(0); json.propertyNames().forEach(summaryFields::add);
        org.junit.jupiter.api.Assertions.assertEquals(Set.of("patientProfileId", "fullName", "phoneCanonical",
                "dateOfBirth", "gender", "hasAccount"), summaryFields);
    }
    @Test void invalidTransportAndBeanValidationUseExistingEnvelope() throws Exception {
        String correlation = UUID.randomUUID().toString();
        mvc.perform(get(ROOT + "/matches").param("phone", "0912345678").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-17"));
        mvc.perform(get(ROOT + "/matches").param("phone", "0912345678").param("dateOfBirth", "not-a-date")
                .with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isBadRequest());
        mvc.perform(post(ROOT).with(csrf()).with(actor(AccountRole.RECEPTIONIST)).contentType("application/json")
                .content(BODY.replace("\"address\":\"Address\"", "\"address\":null")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("address"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG-17"));
        mvc.perform(put(ROOT + "/" + id).with(csrf()).with(actor(AccountRole.RECEPTIONIST))
                .requestAttr(fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter.ATTRIBUTE, correlation)
                .contentType("application/json").content(BODY)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-17"))
                .andExpect(jsonPath("$.error.correlationId").value(correlation));
        verifyNoInteractions(profiles);
    }
    @Test void businessErrorsOptimisticAndUnexpectedRemainScoped() throws Exception {
        when(profiles.matches(any(), any(), any(), any())).thenThrow(new PatientProfileException("PATIENT_MATCH_AMBIGUOUS"));
        mvc.perform(request("matches").with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MSG-18"));
        when(profiles.detail(any(), any())).thenThrow(new PatientProfileException("PATIENT_PROFILE_NOT_FOUND"));
        mvc.perform(request("detail").with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MSG-19"));
        when(profiles.create(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new PatientProfileException("PATIENT_PROFILE_DUPLICATE"));
        mvc.perform(request("create").with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MSG-18"));
        when(profiles.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("profile", id));
        mvc.perform(request("update").with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MSG-16"));
        doThrow(new IllegalStateException("private infrastructure detail")).when(profiles).detail(any(), any());
        mvc.perform(request("detail").with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("MSG-04"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private infrastructure"))));
    }
    @ParameterizedTest
    @CsvSource({"55P03,503,DEPENDENCY_UNAVAILABLE", "57014,503,DEPENDENCY_UNAVAILABLE",
            "40P01,503,DEPENDENCY_UNAVAILABLE", "23505,500,MSG-04", "23514,500,MSG-04"})
    void createClassifiesDatabaseFailuresSeparatelyFromConfirmedDuplicates(String sqlState, int status, String code)
            throws Exception {
        var cause = new SQLException("private database detail", sqlState);
        DataAccessException failure = status == 503
                ? new PessimisticLockingFailureException("Temporary database failure", cause)
                : new DataIntegrityViolationException("Unrelated constraint", cause);
        when(profiles.create(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(failure);

        mvc.perform(request("create").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private database detail"))));
    }
    @Test void oldSearchAndReceptionVerificationRemainAvailable() throws Exception {
        when(profiles.search("An", 0, 20)).thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get(ROOT).param("query", "An").with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isOk());
        when(profiles.getReceptionVerificationProfile(actorId, id)).thenReturn(
                new fu.fa26.sep490.g58.otcms_be.patient.model.ReceptionVerificationProfile(id, "An", LocalDate.of(2000, 1, 2)));
        mvc.perform(get(ROOT + "/" + id + "/reception-verification").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("An"));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
