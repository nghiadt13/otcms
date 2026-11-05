package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalPrescriptionController;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.*;
import fu.fa26.sep490.g58.otcms_be.config.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.request.*;

@WebMvcTest(ClinicalPrescriptionController.class)
@Import({SecurityConfig.class, ClinicalDoctorIdentityResolver.class, ClinicalApiExceptionHandler.class,
        CorrelationIdFilter.class, ClinicalPrescriptionControllerTest.Fixture.class})
class ClinicalPrescriptionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClinicalPrescriptionService prescriptions;
    @MockitoBean DoctorProfileIdentityQueryService doctors;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final UUID accountId = UUID.randomUUID(), doctorId = UUID.randomUUID(), patientId = UUID.randomUUID(), visitId = UUID.randomUUID(), id = UUID.randomUUID();
    final String draftBody = """
            {"items":[{"medicine":"Paracetamol 500mg","dose":"1 viên","frequency":"2 lần/ngày","usage":"Uống sau ăn"}],"version":0}
            """;
    String path() { return "/api/v1/visits/" + visitId + "/prescription"; }
    PrescriptionView view(boolean issued) {
        return new PrescriptionView(id, visitId, patientId, doctorId, issued ? ClinicalPrescriptionStatus.ISSUED : ClinicalPrescriptionStatus.DRAFT,
                issued ? Instant.parse("2026-11-04T10:30:00Z") : null, issued ? 2 : 1,
                List.of(new PrescriptionLine(1, "Paracetamol 500mg", "1 viên", "2 lần/ngày", "Uống sau ăn")));
    }
    @BeforeEach void setUp() {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.of(doctorId));
        when(prescriptions.getPrescription(visitId, doctorId)).thenReturn(view(false));
        when(prescriptions.saveDraft(any())).thenReturn(view(false)); when(prescriptions.issue(any())).thenReturn(view(true));
    }
    MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "get" -> get(path());
            case "put" -> put(path()).contentType("application/json").content(draftBody);
            case "issue" -> post(path() + "/issuance").header("Idempotency-Key", "issue-key").contentType("application/json").content("{\"version\":1}");
            default -> throw new IllegalArgumentException();
        };
    }
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(accountId, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @ParameterizedTest @ValueSource(strings = {"get", "put", "issue"})
    void anonymousIs401ForEveryEndpoint(String operation) throws Exception {
        mvc.perform(request(operation).with(csrf())).andExpect(status().isUnauthorized()); verifyNoInteractions(prescriptions, doctors);
    }
    @ParameterizedTest @EnumSource(value = AccountRole.class, names = "DOCTOR", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherRoleIsForbiddenForEveryEndpoint(AccountRole role) throws Exception {
        for (String operation : List.of("get", "put", "issue")) mvc.perform(request(operation).with(actor(role)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(prescriptions, doctors);
    }
    @ParameterizedTest @ValueSource(strings = {"get", "put", "issue"})
    void doctorWithoutProfileIsForbidden(String operation) throws Exception {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.empty());
        mvc.perform(request(operation).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MSG-40")); verifyNoInteractions(prescriptions);
    }
    @ParameterizedTest @ValueSource(strings = {"get", "put", "issue"})
    void doctorReceivesExactPrivateResponseAndSharedCorrelation(String operation) throws Exception {
        String correlation = UUID.randomUUID().toString();
        String json = mvc.perform(request(operation).with(actor(AccountRole.DOCTOR)).with(csrf()).header("X-Correlation-ID", correlation))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Correlation-ID", correlation))
                .andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.patientProfileId").value(patientId.toString()))
                .andExpect(jsonPath("$.status").value(operation.equals("issue") ? "ISSUED" : "DRAFT"))
                .andExpect(jsonPath("$.version").value(operation.equals("issue") ? 2 : 1))
                .andExpect(jsonPath("$.issuedAt").value(operation.equals("issue") ? "2026-11-04T10:30:00Z" : null))
                .andExpect(jsonPath("$.items[0].lineNumber").value(1)).andExpect(jsonPath("$.items[0].medicine").value("Paracetamol 500mg"))
                .andExpect(jsonPath("$.items[0].dose").value("1 viên")).andExpect(jsonPath("$.items[0].frequency").value("2 lần/ngày"))
                .andExpect(jsonPath("$.items[0].usage").value("Uống sau ăn")).andReturn().getResponse().getContentAsString();
        var tree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);
        assertEquals(Set.of("id", "visitId", "patientProfileId", "status", "issuedAt", "version", "items"), tree.propertyNames());
        assertEquals(Set.of("lineNumber", "medicine", "dose", "frequency", "usage"), tree.get("items").get(0).propertyNames());
    }
    @Test void bodyAndQueryCannotSpoofPatientDoctorStatusOrServerIds() throws Exception {
        String body = draftBody.trim().replace("\"version\":0", "\"version\":0,\"doctorId\":\"" + UUID.randomUUID()
                + "\",\"patientId\":\"" + UUID.randomUUID() + "\",\"status\":\"ISSUED\",\"visitId\":\"" + UUID.randomUUID() + "\"");
        mvc.perform(put(path()).param("doctorId", UUID.randomUUID().toString()).with(actor(AccountRole.DOCTOR)).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isOk());
        var command = ArgumentCaptor.forClass(DraftCommand.class); verify(prescriptions).saveDraft(command.capture());
        assertEquals(doctorId, command.getValue().doctorProfileId()); assertEquals(visitId, command.getValue().visitId());
        assertEquals(0, command.getValue().version()); verify(doctors).findDoctorProfileId(accountId);
    }
    @Test void issuanceUsesOnlySavedVersionAndAuthenticatedProfile() throws Exception {
        mvc.perform(request("issue").with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isOk());
        verify(prescriptions).issue(new IssuanceCommand(visitId, doctorId, 1L, "issue-key"));
        verify(prescriptions, never()).saveDraft(any());
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"items\":[],\"version\":0}",
            "{\"items\":[null],\"version\":0}", "{\"items\":[{\"medicine\":\" \"}],\"version\":0}"})
    void invalidDraftTransportIsSafelyRejected(String body) throws Exception {
        mvc.perform(put(path()).contentType("application/json").content(body).with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED")); verifyNoInteractions(prescriptions);
    }
    @ParameterizedTest @ValueSource(strings = {"\"0\"", "0.5", "-1", "9223372036854775808"})
    void nonIntegerOrInvalidVersionNeverReachesService(String version) throws Exception {
        mvc.perform(put(path()).contentType("application/json").content(draftBody.replace("\"version\":0", "\"version\":" + version))
                .with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isBadRequest()); verifyNoInteractions(prescriptions);
    }
    @Test void missingIssuanceKeyIs400() throws Exception {
        mvc.perform(post(path() + "/issuance").contentType("application/json").content("{\"version\":1}")
                .with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isBadRequest()); verifyNoInteractions(prescriptions);
    }
    @ParameterizedTest @CsvSource({"MSG-39,400", "MSG-40,403", "MSG-41,404", "MSG-42,409", "IDEMPOTENCY_CONFLICT,409", "DEPENDENCY_UNAVAILABLE,503"})
    void domainErrorsFollowExistingClinicalEnvelope(String code, int status) throws Exception {
        when(prescriptions.issue(any())).thenThrow(new ClinicalOperationException(code));
        mvc.perform(request("issue").with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code)).andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void databaseAndUnexpectedErrorsAreSanitized() throws Exception {
        when(prescriptions.getPrescription(visitId, doctorId)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL"));
        mvc.perform(request("get").with(actor(AccountRole.DOCTOR))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        when(prescriptions.issue(any())).thenThrow(new IllegalStateException("private failure"));
        mvc.perform(request("issue").with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("MSG-04"));
    }
    @ParameterizedTest @ValueSource(strings = {"put", "issue"})
    void existingCsrfPolicyIsPreserved(String operation) throws Exception {
        mvc.perform(request(operation).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden()); verifyNoInteractions(prescriptions);
    }
    @Test void authorityCannotSpoofTheAuthenticatedPrincipalRole() throws Exception {
        var mismatch = authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(accountId, AccountRole.RECEPTIONIST, null),
                null, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        mvc.perform(request("get").with(mismatch)).andExpect(status().isForbidden()); verifyNoInteractions(prescriptions);
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
