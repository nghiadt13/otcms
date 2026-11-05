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
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalMedicalDocumentController;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.*;
import fu.fa26.sep490.g58.otcms_be.config.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.*;

@WebMvcTest(ClinicalMedicalDocumentController.class)
@Import({SecurityConfig.class, ClinicalApiExceptionHandler.class, CorrelationIdFilter.class, ClinicalMedicalDocumentControllerTest.Fixture.class})
class ClinicalMedicalDocumentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClinicalMedicalDocumentService documents;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final MedicalDocumentFixture f = new MedicalDocumentFixture();
    UUID id;
    String collection() { return "/api/v1/visits/" + f.visitId + "/medical-documents"; }
    String item() { return "/api/v1/medical-documents/" + id; }
    String mutation() { return collection() + "/" + id; }
    @BeforeEach void setup() {
        id = f.uploadReport().id();
        when(documents.upload(any(), any(), any(), any(), any())).thenAnswer(call -> f.service.upload(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4)));
        when(documents.update(any(), any(), any(), any(), any(), any())).thenAnswer(call -> f.service.update(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4), call.getArgument(5)));
        when(documents.release(any(), any(), any(), any())).thenAnswer(call -> f.service.release(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(documents.list(any(), any())).thenAnswer(call -> f.service.list(call.getArgument(0), call.getArgument(1)));
        when(documents.get(any(), any())).thenAnswer(call -> f.service.get(call.getArgument(0), call.getArgument(1)));
        when(documents.content(any(), any())).thenAnswer(call -> f.service.content(call.getArgument(0), call.getArgument(1)));
    }
    RequestPostProcessor actor(AccountRole role) {
        UUID account = switch (role) { case DOCTOR -> f.doctorAccountId; case RECEPTIONIST -> f.receptionistAccountId; case PATIENT -> f.patientAccountId; default -> UUID.randomUUID(); };
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    MockMultipartHttpServletRequestBuilder upload(String metadata, byte[] content) {
        return multipart(collection()).file(new MockMultipartFile("metadata", "metadata.json", "application/json", metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("file", "report.pdf", "application/pdf", content));
    }
    MockMultipartHttpServletRequestBuilder update(String metadata, byte[] content) {
        var builder = multipart(org.springframework.http.HttpMethod.PUT, mutation())
                .file(new MockMultipartFile("metadata", "metadata.json", "application/json", metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (content != null) builder.file(new MockMultipartFile("file", "new.pdf", "application/pdf", content)); return builder;
    }
    String metadata() { return "{\"documentType\":\"CLINIC_EXAMINATION_REPORT\",\"title\":\"Report\"}"; }

    @Test void anonymousAndUnrelatedRolesAreDenied() throws Exception {
        mvc.perform(get(item())).andExpect(status().isUnauthorized());
        for (AccountRole role : List.of(AccountRole.ADMIN, AccountRole.IMAGING_TECHNICIAN)) {
            mvc.perform(get(item()).with(actor(role))).andExpect(status().isForbidden());
            mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("x")).with(actor(role)).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(documents);
    }
    @Test void doctorUploadContractCannotSpoofPatientVisitReleaseOrDoctor() throws Exception {
        String spoof = metadata().replace("\"title\":\"Report\"", "\"title\":\"Report\",\"patientProfileId\":\"" + UUID.randomUUID()
                + "\",\"visitId\":\"" + UUID.randomUUID() + "\",\"doctorId\":\"" + UUID.randomUUID() + "\",\"released\":true");
        String correlation = UUID.randomUUID().toString();
        String body = mvc.perform(upload(spoof, MedicalDocumentFixture.pdf("upload")).with(actor(AccountRole.DOCTOR)).with(csrf()).header("X-Correlation-ID", correlation))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.patientProfileId").value(f.patientId.toString()))
                .andExpect(jsonPath("$.visitId").value(f.visitId.toString())).andExpect(jsonPath("$.released").value(false))
                .andExpect(jsonPath("$.version").value(1)).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Correlation-ID", correlation)).andReturn().getResponse().getContentAsString();
        var tree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
        assertEquals(Set.of("id", "visitId", "patientProfileId", "documentType", "title", "originalFilename", "mediaType", "byteSize",
                "released", "releasedAt", "version", "createdAt", "updatedAt"), tree.propertyNames());
    }
    @Test void wrongOrProfilelessDoctorAndUnavailableVisitAreDenied() throws Exception {
        when(f.doctors.findDoctorProfileId(f.doctorAccountId)).thenReturn(Optional.empty());
        mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("x")).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isForbidden());
        when(f.doctors.findDoctorProfileId(f.doctorAccountId)).thenReturn(Optional.of(f.doctorId)); f.exists = false;
        mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("x")).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isNotFound());
        f.exists = true; f.allowed = false;
        mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("x")).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isConflict());
    }
    @Test void receptionUploadsReadsClinicReportsButCannotUpdateOrRelease() throws Exception {
        mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("reception")).with(actor(AccountRole.RECEPTIONIST)).with(csrf())).andExpect(status().isCreated());
        mvc.perform(get(item()).with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isOk());
        mvc.perform(update("{\"title\":\"bad\",\"version\":1}", null).with(actor(AccountRole.RECEPTIONIST)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(mutation() + "/release").contentType("application/json").content("{\"version\":1}").with(actor(AccountRole.RECEPTIONIST)).with(csrf())).andExpect(status().isForbidden());
        when(f.receptionists.isActiveReceptionistAccount(f.receptionistAccountId)).thenReturn(false);
        mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("x")).with(actor(AccountRole.RECEPTIONIST)).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void patientOwnAndDependentReadOnlyReleasedMetadataAndBytes() throws Exception {
        mvc.perform(get(collection()).with(actor(AccountRole.PATIENT))).andExpect(status().isOk()).andExpect(content().json("{\"items\":[]}"));
        mvc.perform(get(item() + "/content").with(actor(AccountRole.PATIENT))).andExpect(status().isNotFound());
        f.completed(); f.service.release(f.doctor(), f.visitId, id, 1L);
        mvc.perform(get(item() + "/content").with(actor(AccountRole.PATIENT))).andExpect(status().isOk())
                .andExpect(content().bytes(MedicalDocumentFixture.pdf("initial")))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
        f.dependent(true); mvc.perform(get(item()).with(actor(AccountRole.PATIENT))).andExpect(status().isOk());
        when(f.links.findAllByRequesterAccountIdAndState(f.patientAccountId, fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State.CONFIRMED)).thenReturn(List.of());
        mvc.perform(get(item() + "/content").with(actor(AccountRole.PATIENT))).andExpect(status().isForbidden());
        mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("x")).with(actor(AccountRole.PATIENT)).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void replacementReleaseAndStaleVersionUseCurrentErrorContract() throws Exception {
        mvc.perform(update("{\"title\":\"New\",\"version\":1}", MedicalDocumentFixture.pdf("new")).with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(update("{\"title\":\"Stale\",\"version\":1}", null).with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-42"));
        f.completed(); mvc.perform(post(mutation() + "/release").contentType("application/json").content("{\"version\":2}").with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.released").value(true));
        mvc.perform(update("{\"title\":\"After release\",\"version\":3}", null).with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-42"));
    }
    @Test void anotherVisitAndInvalidIdsDoNotAccessRecord() throws Exception {
        String other = "/api/v1/visits/" + UUID.randomUUID() + "/medical-documents/" + id;
        var request = multipart(org.springframework.http.HttpMethod.PUT, other).file(new MockMultipartFile("metadata", "", "application/json", "{\"title\":\"bad\",\"version\":1}".getBytes()));
        mvc.perform(request.with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/medical-documents/not-a-uuid").with(actor(AccountRole.DOCTOR))).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"documentType\":\"UNKNOWN\",\"title\":\"x\"}", "not-json"})
    void malformedMetadataIsSafe400(String metadata) throws Exception {
        mvc.perform(upload(metadata, MedicalDocumentFixture.pdf("x")).with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isBadRequest()); verifyNoInteractions(documents);
    }
    @Test void missingPartEmptyFileAndObviousFakePdfAreRejected() throws Exception {
        mvc.perform(multipart(collection()).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(upload(metadata(), new byte[0]).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-47"));
        mvc.perform(upload(metadata(), "<html>fake PDF</html>".getBytes()).with(actor(AccountRole.DOCTOR)).with(csrf())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-47"));
    }
    @ParameterizedTest @ValueSource(strings = {"\"1\"", "1.5", "0", "-1", "9223372036854775808"})
    void versionMustBePositiveInteger(String version) throws Exception {
        mvc.perform(update("{\"title\":\"x\",\"version\":" + version + "}", null).with(actor(AccountRole.DOCTOR)).with(csrf()))
                .andExpect(status().isBadRequest()); verifyNoInteractions(documents);
    }
    @Test void csrfAndPrincipalRoleChecksArePreserved() throws Exception {
        mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("x")).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden());
        var mismatch = authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(f.patientAccountId, AccountRole.PATIENT, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        mvc.perform(upload(metadata(), MedicalDocumentFixture.pdf("x")).with(mismatch).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void persistenceFailureIsSanitized() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL")).when(documents).get(any(), any());
        mvc.perform(get(item()).with(actor(AccountRole.DOCTOR))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
