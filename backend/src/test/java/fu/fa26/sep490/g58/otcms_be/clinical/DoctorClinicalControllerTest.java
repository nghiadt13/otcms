package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.*;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.*;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({ClinicalExaminationController.class, AfterVisitAdviceController.class})
@Import({SecurityConfig.class, ClinicalApiExceptionHandler.class, ClinicalDoctorIdentityResolver.class,
        CorrelationIdFilter.class, DoctorClinicalControllerTest.Fixture.class})
class DoctorClinicalControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClinicalExaminationService examinations;
    @MockitoBean AfterVisitAdviceService advice;
    @MockitoBean DoctorProfileIdentityQueryService doctors;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    private final UUID visitId = UUID.randomUUID(), accountId = UUID.randomUUID(), doctorId = UUID.randomUUID();
    private final UUID examinationId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-04T10:00:00Z");
    private static final String EXAM_BODY = """
            {"reasonForVisit":"Pain","symptoms":"Swelling","examinationFindings":"Tenderness",
             "injuryBodySite":"Knee","injurySide":"LEFT","injuryMechanism":"Twist",
             "diagnoses":[{"code":"M17.0","type":"PRIMARY"}]}
            """;
    private static final String ADVICE_BODY = """
            {"homeCareAdvice":"Rest","activityAdvice":"Avoid impact","rehabilitationAdvice":"Exercise",
             "warningSigns":"Swelling","followUpRecommendation":"Review"}
            """;

    @BeforeEach void setUp() {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.of(doctorId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"examGet", "draft", "complete", "adviceGet", "advicePut"})
    void allEndpointsRejectAnonymousAndEveryNonDoctorRole(String operation) throws Exception {
        mvc.perform(request(operation).with(csrf())).andExpect(status().isUnauthorized());
        for (var role : AccountRole.values()) {
            if (role == AccountRole.DOCTOR) continue;
            mvc.perform(request(operation).with(csrf()).with(actor(role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MSG-40"));
        }
        verifyNoInteractions(examinations, advice, doctors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"examGet", "draft", "complete", "adviceGet", "advicePut"})
    void doctorWithoutProfileCannotInvokeAnyClinicalService(String operation) throws Exception {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.empty());
        mvc.perform(request(operation).with(csrf()).with(actor(AccountRole.DOCTOR)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MSG-40"));
        verifyNoInteractions(examinations, advice);
    }

    @Test void examinationGetResolvesDoctorAndSerializesExactResponseWhitelist() throws Exception {
        when(examinations.getExamination(visitId, doctorId)).thenReturn(examination(ClinicalExaminationStatus.DRAFT));
        String body = mvc.perform(request("examGet").with(actor(AccountRole.DOCTOR)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(examinationId.toString()))
                .andExpect(jsonPath("$.visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.diagnoses[0].code").value("M17.0"))
                .andExpect(jsonPath("$.diagnoses[0].type").value("PRIMARY"))
                .andExpect(jsonPath("$.version").value(3))
                .andReturn().getResponse().getContentAsString();
        assertFields(body, Set.of("id", "visitId", "reasonForVisit", "symptoms", "examinationFindings",
                "injuryBodySite", "injurySide", "injuryMechanism", "status", "diagnoses", "version"));
        verify(doctors).findDoctorProfileId(accountId);
        verify(examinations).getExamination(visitId, doctorId);
    }

    @Test void draftMapsEveryFieldAndIgnoresSpoofedIdentityAndState() throws Exception {
        when(examinations.saveDraft(any())).thenReturn(examination(ClinicalExaminationStatus.DRAFT));
        String spoof = EXAM_BODY.strip().replaceFirst("\\{", "{\"doctorId\":\"" + UUID.randomUUID()
                + "\",\"doctorProfileId\":\"" + UUID.randomUUID() + "\",\"accountId\":\"" + UUID.randomUUID()
                + "\",\"visitId\":\"" + UUID.randomUUID() + "\",\"patientId\":\"" + UUID.randomUUID()
                + "\",\"status\":\"COMPLETED\",\"version\":999,");
        mvc.perform(put(examPath()).with(actor(AccountRole.DOCTOR)).with(csrf())
                .contentType("application/json").content(spoof)).andExpect(status().isOk());
        var capture = ArgumentCaptor.forClass(ClinicalExaminationService.ExaminationCommand.class);
        verify(examinations).saveDraft(capture.capture());
        var command = capture.getValue();
        assertEquals(visitId, command.visitId());
        assertEquals(doctorId, command.doctorId());
        assertEquals("Pain", command.reasonForVisit());
        assertEquals("Swelling", command.symptoms());
        assertEquals("Tenderness", command.examinationFindings());
        assertEquals("Knee", command.injuryBodySite());
        assertEquals("LEFT", command.injurySide());
        assertEquals("Twist", command.injuryMechanism());
        assertEquals(List.of(new ClinicalExaminationService.Diagnosis("M17.0", DiagnosisType.PRIMARY)), command.diagnoses());
        verify(examinations, never()).completeExamination(any());
    }

    @Test void draftAllowsIncompleteBodyAndCompletionCallsOnlyDomainCompletion() throws Exception {
        when(examinations.saveDraft(any())).thenReturn(examination(ClinicalExaminationStatus.DRAFT));
        mvc.perform(put(examPath()).with(actor(AccountRole.DOCTOR)).with(csrf()).contentType("application/json")
                .content("{}")).andExpect(status().isOk());
        verify(examinations).saveDraft(new ClinicalExaminationService.ExaminationCommand(
                visitId, doctorId, null, null, null, null, null, null, null));
        clearInvocations(examinations);
        when(examinations.completeExamination(any())).thenReturn(examination(ClinicalExaminationStatus.COMPLETED));
        mvc.perform(request("complete").with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(examinations).completeExamination(argThat(command -> command.visitId().equals(visitId)
                && command.doctorId().equals(doctorId) && command.reasonForVisit().equals("Pain")));
        verify(examinations, never()).saveDraft(any());
    }

    @Test void adviceGetSerializesExactAdviceFieldsAndUsesResolvedDoctor() throws Exception {
        when(advice.getAdvice(visitId, doctorId)).thenReturn(adviceView());
        String body = mvc.perform(request("adviceGet").with(actor(AccountRole.DOCTOR)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.homeCareAdvice").value("Rest"))
                .andExpect(jsonPath("$.activityAdvice").value("Avoid impact"))
                .andExpect(jsonPath("$.rehabilitationAdvice").value("Exercise"))
                .andExpect(jsonPath("$.warningSigns").value("Swelling"))
                .andExpect(jsonPath("$.followUpRecommendation").value("Review"))
                .andExpect(jsonPath("$.examinationStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.updatedAt").value(now.toString())).andExpect(jsonPath("$.version").value(3))
                .andReturn().getResponse().getContentAsString();
        assertFields(body, Set.of("visitId", "homeCareAdvice", "activityAdvice", "rehabilitationAdvice", "warningSigns",
                "followUpRecommendation", "examinationStatus", "updatedAt", "version", "followUpIndicated"));
        verify(advice).getAdvice(visitId, doctorId);
    }

    @Test void advicePutUsesPathAndAuthenticatedDoctorDespiteSpoofedBodyFields() throws Exception {
        when(advice.recordAdvice(any())).thenReturn(adviceView());
        String spoof = ADVICE_BODY.strip().replaceFirst("\\{", "{\"doctorId\":\"" + UUID.randomUUID()
                + "\",\"visitId\":\"" + UUID.randomUUID() + "\",\"patientId\":\"" + UUID.randomUUID() + "\",");
        String correlation = UUID.randomUUID().toString();
        mvc.perform(put(advicePath()).with(actor(AccountRole.DOCTOR)).with(csrf()).header("X-Correlation-ID", correlation)
                .contentType("application/json").content(spoof)).andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", correlation))
                .andExpect(jsonPath("$.examinationStatus").value("COMPLETED"));
        verify(advice).recordAdvice(new AfterVisitAdviceService.AdviceCommand(visitId, doctorId,
                "Rest", "Avoid impact", "Exercise", "Swelling", "Review"));
    }

    @Test void adviceMissingExaminationAndEmptyAdviceUseDomainErrorContract() throws Exception {
        when(advice.getAdvice(visitId, doctorId)).thenThrow(new ClinicalOperationException("MSG-41"));
        mvc.perform(request("adviceGet").with(actor(AccountRole.DOCTOR))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MSG-41"));
        when(advice.recordAdvice(any())).thenThrow(new ClinicalOperationException("MSG-39"));
        mvc.perform(put(advicePath()).with(actor(AccountRole.DOCTOR)).with(csrf()).contentType("application/json")
                .content("{\"homeCareAdvice\":\"  \"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-39"));
        verify(advice).recordAdvice(new AfterVisitAdviceService.AdviceCommand(visitId, doctorId, "  ", null, null, null, null));
    }

    @ParameterizedTest
    @CsvSource({"MSG-39,400", "MSG-08,400", "MSG-40,403", "MSG-14,403", "MSG-41,404", "MSG-42,409", "MSG-04,500"})
    void completionPreservesDomainCodesWithSafeHttpMapping(String code, int expectedStatus) throws Exception {
        when(examinations.completeExamination(any())).thenThrow(new ClinicalOperationException(code));
        String correlation = UUID.randomUUID().toString();
        mvc.perform(request("complete").with(actor(AccountRole.DOCTOR)).with(csrf()).header("X-Correlation-ID", correlation))
                .andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.correlationId").value(correlation))
                .andExpect(header().string("X-Correlation-ID", correlation))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @ParameterizedTest @ValueSource(strings = {"injuryBodySite", "injurySide"})
    void transportLimitsRespectPersistedLengths(String field) throws Exception {
        int max = field.equals("injuryBodySite") ? 120 : 24;
        mvc.perform(put(examPath()).with(actor(AccountRole.DOCTOR)).with(csrf()).contentType("application/json")
                .content("{\"" + field + "\":\"" + "X".repeat(max + 1) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG-39"));
        verifyNoInteractions(examinations);
    }

    @Test void malformedJsonEnumsNullDiagnosisAndInvalidPathUseTransportEnvelope() throws Exception {
        for (String body : List.of("{", "{\"diagnoses\":[{\"type\":\"UNKNOWN\"}]}", "{\"diagnoses\":[null]}")) {
            mvc.perform(put(examPath()).with(actor(AccountRole.DOCTOR)).with(csrf()).contentType("application/json")
                    .content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(get("/api/v1/visits/not-a-uuid/examination").with(actor(AccountRole.DOCTOR)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(put(advicePath()).with(actor(AccountRole.DOCTOR)).with(csrf()).contentType("application/json")
                .content("{")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(examinations, advice);
    }

    @Test void staleWriteDatabaseFailureAndUnexpectedErrorsAreSanitized() throws Exception {
        when(examinations.saveDraft(any())).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("exam", visitId));
        mvc.perform(request("draft").with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MSG-42"));
        when(advice.getAdvice(any(), any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal SQL detail"));
        mvc.perform(request("adviceGet").with(actor(AccountRole.DOCTOR))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal SQL detail"))));
        doThrow(new IllegalStateException("internal implementation detail")).when(advice).getAdvice(any(), any());
        mvc.perform(request("adviceGet").with(actor(AccountRole.DOCTOR))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("MSG-04"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal implementation detail"))));
    }

    @ParameterizedTest @ValueSource(strings = {"draft", "complete", "advicePut"})
    void mutationsRetainExistingCsrfProtection(String operation) throws Exception {
        mvc.perform(request(operation).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden());
        verifyNoInteractions(examinations, advice);
    }

    @Test void principalRoleIsCheckedEvenWhenAuthoritiesClaimDoctor() throws Exception {
        var mismatch = authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccount(accountId, AccountRole.RECEPTIONIST, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        mvc.perform(request("examGet").with(mismatch)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MSG-40"));
        mvc.perform(request("adviceGet").with(user("doctor").roles("DOCTOR"))).andExpect(status().isForbidden());
        verifyNoInteractions(examinations, advice, doctors);
    }

    @Test void requestsHaveOnlyDeclaredClinicalFields() throws Exception {
        assertRecordFields(ExaminationRequest.class, Set.of("reasonForVisit", "symptoms", "examinationFindings",
                "injuryBodySite", "injurySide", "injuryMechanism", "diagnoses"));
        assertRecordFields(DiagnosisRequest.class, Set.of("code", "type"));
        assertRecordFields(AdviceRequest.class, Set.of("homeCareAdvice", "activityAdvice", "rehabilitationAdvice",
                "warningSigns", "followUpRecommendation", "followUpIndicated"));
    }

    @Test void actualDomainVisitGuardsReachCurrentHttpErrorsWithoutWrites() throws Exception {
        var contexts = mock(VisitClinicalContextPort.class);
        var examRows = mock(fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository.class);
        var diagnosisRows = mock(fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalDiagnosisRepository.class);
        var realExaminations = new fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalExaminationServiceImpl(
                contexts, examRows, diagnosisRows, java.time.Clock.systemUTC(), mock(fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService.class));
        var realAdvice = new fu.fa26.sep490.g58.otcms_be.clinical.service.impl.AfterVisitAdviceServiceImpl(
                contexts, examRows, java.time.Clock.systemUTC());
        when(examinations.saveDraft(any())).thenAnswer(invocation -> realExaminations.saveDraft(invocation.getArgument(0)));
        when(advice.recordAdvice(any())).thenAnswer(invocation -> realAdvice.recordAdvice(invocation.getArgument(0)));

        mvc.perform(request("draft").with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-41"));
        when(contexts.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, UUID.randomUUID(), UUID.randomUUID(), true));
        mvc.perform(request("draft").with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MSG-40"));
        when(contexts.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, UUID.randomUUID(), doctorId, false));
        mvc.perform(request("draft").with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-42"));
        mvc.perform(request("advicePut").with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-42"));
        verifyNoInteractions(examRows, diagnosisRows);
    }

    private String examPath() { return "/api/v1/visits/" + visitId + "/examination"; }
    private String advicePath() { return "/api/v1/visits/" + visitId + "/after-visit-advice"; }

    private MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "examGet" -> get(examPath());
            case "adviceGet" -> get(advicePath());
            case "draft" -> put(examPath()).contentType("application/json").content(EXAM_BODY);
            case "complete" -> post(examPath() + "/completion").contentType("application/json").content(EXAM_BODY);
            default -> put(advicePath()).contentType("application/json").content(ADVICE_BODY);
        };
    }

    private RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(accountId, role, null),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private ClinicalExaminationService.ExaminationView examination(ClinicalExaminationStatus status) {
        return new ClinicalExaminationService.ExaminationView(examinationId, visitId, "Pain", "Swelling", "Tenderness",
                "Knee", "LEFT", "Twist", status,
                List.of(new ClinicalExaminationService.Diagnosis("M17.0", DiagnosisType.PRIMARY)), 3L);
    }

    private AfterVisitAdviceService.AdviceView adviceView() {
        return new AfterVisitAdviceService.AdviceView(visitId, "Rest", "Avoid impact", "Exercise", "Swelling", "Review",
                ClinicalExaminationStatus.COMPLETED, now, 3L);
    }

    private static void assertFields(String json, Set<String> expected) {
        assertEquals(expected, tools.jackson.databind.json.JsonMapper.builder().build().readTree(json).propertyNames());
    }

    private static void assertRecordFields(Class<?> record, Set<String> expected) {
        assertEquals(expected, Arrays.stream(record.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
