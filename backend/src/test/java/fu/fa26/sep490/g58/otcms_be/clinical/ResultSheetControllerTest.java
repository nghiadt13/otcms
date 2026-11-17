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
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ResultSheetController;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalMedicalDocumentRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalMedicalDocumentAccess;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet.ResultSheetIssuer;
import fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet.ResultSheetQueryService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorCareRelationQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ResultSheetController.class)
@Import({SecurityConfig.class, ClinicalApiExceptionHandler.class, CorrelationIdFilter.class,
        ClinicalMedicalDocumentAccess.class, ResultSheetQueryService.class, ResultSheetControllerTest.Fixture.class})
class ResultSheetControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    @MockitoBean ClinicalDoctorIdentityResolver doctors;
    @MockitoBean VisitClinicalContextPort clinicalVisits;
    @MockitoBean VisitClinicalCareContextService careVisits;
    @MockitoBean ReceptionistAccountAccessService receptionists;
    @MockitoBean PatientProfileAccessService patients;
    @MockitoBean VisitDoctorCareRelationQueryService care;
    @MockitoBean VisitReviewSourceService visits;
    @MockitoBean VisitExamQueueService queue;
    @MockitoBean ClinicalMedicalDocumentRepository documents;
    @MockitoBean ResultSheetIssuer issuer;

    private final UUID visit = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID();
    private final UUID patientAccount = UUID.randomUUID(), doctorAccount = UUID.randomUUID(), receptionist = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-17T03:00:00Z");
    private String path() { return "/api/v1/visits/" + visit + "/result-sheets"; }

    @BeforeEach void setup() {
        when(visits.find(visit)).thenReturn(Optional.of(source("CLOSED")));
        when(doctors.requireDoctorProfile(any())).thenReturn(doctor);
        when(queue.find(visit)).thenReturn(Optional.of(new ExamQueueEntry(visit, doctor, null, null, 1,
                VisitExamStatus.COMPLETED, LocalDate.of(2026, 11, 17), now, now, null, now)));
        when(receptionists.isActiveReceptionistAccount(receptionist)).thenReturn(true);
        when(patients.canAccess(patientAccount, patient)).thenReturn(true);
    }

    @Test void listRequiresEligibleRoleAndPatientRelationship() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).with(actor(AccountRole.ADMIN, UUID.randomUUID()))).andExpect(status().isForbidden());
        mvc.perform(get(path()).with(actor(AccountRole.PATIENT, patientAccount))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(path()).with(actor(AccountRole.RECEPTIONIST, receptionist))).andExpect(status().isOk());
        mvc.perform(get(path()).with(actor(AccountRole.DOCTOR, doctorAccount))).andExpect(status().isOk());
        when(patients.canAccess(patientAccount, patient)).thenReturn(false);
        mvc.perform(get(path()).with(actor(AccountRole.PATIENT, patientAccount))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MSG-40"));
    }

    @Test void patientCannotSeeResultsBeforeVisitIsClosed() throws Exception {
        when(visits.find(visit)).thenReturn(Optional.of(source("OPEN")));
        mvc.perform(get(path()).with(actor(AccountRole.PATIENT, patientAccount))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MSG-42"));
        verifyNoInteractions(documents);
    }

    @Test void regenerationRequiresResponsibleDoctorAndClosedVisit() throws Exception {
        mvc.perform(post(path() + "/regeneration").with(csrf()).with(actor(AccountRole.PATIENT, patientAccount)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path() + "/regeneration").with(csrf()).with(actor(AccountRole.RECEPTIONIST, receptionist)))
                .andExpect(status().isForbidden());
        UUID other = UUID.randomUUID();
        when(doctors.requireDoctorProfile(any())).thenReturn(other);
        when(care.hasCurrentCare(other, patient)).thenReturn(true);
        mvc.perform(post(path() + "/regeneration").with(csrf()).with(actor(AccountRole.DOCTOR, doctorAccount)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(issuer);
        when(doctors.requireDoctorProfile(any())).thenReturn(doctor);
        mvc.perform(post(path() + "/regeneration").with(csrf()).with(actor(AccountRole.DOCTOR, doctorAccount)))
                .andExpect(status().isOk());
        verify(issuer).issue(visit, doctor);
        when(visits.find(visit)).thenReturn(Optional.of(source("OPEN")));
        mvc.perform(post(path() + "/regeneration").with(csrf()).with(actor(AccountRole.DOCTOR, doctorAccount)))
                .andExpect(status().isConflict());
        verifyNoMoreInteractions(issuer);
    }

    private VisitReviewSourceService.Source source(String status) {
        return new VisitReviewSourceService.Source(visit, patient, status, now, "", "");
    }
    private RequestPostProcessor actor(AccountRole role, UUID account) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
