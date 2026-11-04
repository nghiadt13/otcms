package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.DoctorActiveVisitController;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.DoctorActiveVisitReadServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService.ClinicalIdentity;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorActiveCareReadService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorActiveCareReadService.DoctorActiveVisit;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@WebMvcTest(DoctorActiveVisitController.class)
@Import({SecurityConfig.class, ClinicalApiExceptionHandler.class, ClinicalDoctorIdentityResolver.class,
        DoctorActiveVisitReadServiceImpl.class, CorrelationIdFilter.class, DoctorActiveVisitControllerTest.Fixture.class})
class DoctorActiveVisitControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VisitDoctorActiveCareReadService visits;
    @MockitoBean PatientClinicalIdentityQueryService patients;
    @MockitoBean DoctorProfileIdentityQueryService doctors;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    private static final String PATH = "/api/v1/doctor/visits/active";
    private final UUID accountId = UUID.randomUUID(), doctorId = UUID.randomUUID(), visitId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID(), assignmentId = UUID.randomUUID(), appointmentId = UUID.randomUUID();

    @BeforeEach void setUp() {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.of(doctorId));
        when(visits.findActiveVisits(doctorId)).thenReturn(List.of());
    }

    @Test void anonymousAndEveryNonDoctorRoleAreRejectedBeforeDataLookup() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (var role : AccountRole.values()) {
            if (role == AccountRole.DOCTOR) continue;
            mvc.perform(get(PATH).with(actor(role))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("MSG-40"));
        }
        verifyNoInteractions(doctors, visits, patients);
    }

    @Test void doctorWithoutProfileIsForbidden() throws Exception {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.empty());
        mvc.perform(get(PATH).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MSG-40"));
        verifyNoInteractions(visits, patients);
    }

    @Test void noActiveVisitIsSuccessfulEmptyListWithNoStoreAndCorrelation() throws Exception {
        String correlation = UUID.randomUUID().toString();
        mvc.perform(get(PATH).with(actor(AccountRole.DOCTOR)).header("X-Correlation-ID", correlation))
                .andExpect(status().isOk()).andExpect(content().json("{\"items\":[]}"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Correlation-ID", correlation));
        verify(visits).findActiveVisits(doctorId); verifyNoInteractions(patients);
    }

    @ParameterizedTest @ValueSource(strings = {"SCHEDULED", "WALK_IN"})
    void responseContainsExactActiveCareAndMinimalIdentityFields(String source) throws Exception {
        var start = Instant.parse("2026-11-04T14:00:00Z");
        String step = source.equals("SCHEDULED") ? "SCHEDULED_APPOINTMENT" : "EXAMINATION";
        UUID linked = source.equals("SCHEDULED") ? appointmentId : null;
        when(visits.findActiveVisits(doctorId)).thenReturn(List.of(new DoctorActiveVisit(visitId, patientId, source,
                linked, assignmentId, step, start, start.plusSeconds(1800))));
        when(patients.findClinicalIdentities(List.of(patientId))).thenReturn(Map.of(patientId,
                new ClinicalIdentity(patientId, "Nguyen Van A", LocalDate.of(2000, 1, 1), "MALE")));
        String json = mvc.perform(get(PATH).with(actor(AccountRole.DOCTOR))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.items[0].patientProfileId").value(patientId.toString()))
                .andExpect(jsonPath("$.items[0].patient.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.items[0].patient.dateOfBirth").value("2000-01-01"))
                .andExpect(jsonPath("$.items[0].patient.gender").value("MALE"))
                .andExpect(jsonPath("$.items[0].visitSource").value(source))
                .andExpect(jsonPath("$.items[0].linkedAppointmentId").value(linked == null ? null : linked.toString()))
                .andExpect(jsonPath("$.items[0].assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.items[0].stepCode").value(step))
                .andExpect(jsonPath("$.items[0].startedAt").value(start.toString()))
                .andExpect(jsonPath("$.items[0].expectedEnd").value(start.plusSeconds(1800).toString()))
                .andReturn().getResponse().getContentAsString();
        var tree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);
        assertEquals(Set.of("items"), tree.propertyNames());
        var item = tree.get("items").get(0);
        assertEquals(Set.of("visitId", "patientProfileId", "patient", "visitSource", "linkedAppointmentId",
                "assignmentId", "stepCode", "startedAt", "expectedEnd"), item.propertyNames());
        assertEquals(Set.of("fullName", "dateOfBirth", "gender"), item.get("patient").propertyNames());
        verify(patients).findClinicalIdentities(List.of(patientId));
    }

    @Test void clientDoctorAndPatientQueryParametersCannotSelectAnotherScope() throws Exception {
        mvc.perform(get(PATH).with(actor(AccountRole.DOCTOR)).param("doctorId", UUID.randomUUID().toString())
                .param("doctorProfileId", UUID.randomUUID().toString()).param("accountId", UUID.randomUUID().toString())
                .param("patientProfileId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(content().json("{\"items\":[]}"));
        verify(doctors).findDoctorProfileId(accountId);
        verify(visits).findActiveVisits(doctorId); verifyNoMoreInteractions(visits);
    }

    @Test void missingPatientProducesDependencyErrorWithoutPartialItems() throws Exception {
        when(visits.findActiveVisits(doctorId)).thenReturn(List.of(new DoctorActiveVisit(visitId, patientId, "WALK_IN",
                null, assignmentId, "EXAMINATION", Instant.EPOCH, Instant.EPOCH.plusSeconds(1800))));
        when(patients.findClinicalIdentities(List.of(patientId))).thenReturn(Map.of());
        mvc.perform(get(PATH).with(actor(AccountRole.DOCTOR))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.items").doesNotExist()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void databaseErrorsAreSanitizedAndDoNotReturnSuccessfulEmptyData() throws Exception {
        when(visits.findActiveVisits(doctorId)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal persistence detail"));
        mvc.perform(get(PATH).with(actor(AccountRole.DOCTOR))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal persistence detail"))));
    }

    private RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(accountId, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
