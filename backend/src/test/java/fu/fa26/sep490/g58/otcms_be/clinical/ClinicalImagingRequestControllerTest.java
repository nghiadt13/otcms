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
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalImagingRequestController;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.*;
import fu.fa26.sep490.g58.otcms_be.config.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({ClinicalImagingRequestController.class, fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalRoundWorkflowController.class})
@Import({SecurityConfig.class, ClinicalApiExceptionHandler.class, ClinicalDoctorIdentityResolver.class,
        CorrelationIdFilter.class, ClinicalImagingRequestControllerTest.Fixture.class})
class ClinicalImagingRequestControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClinicalImagingRequestService imaging;
    @MockitoBean ClinicalRoundWorkflowService workflow;
    @MockitoBean DoctorProfileIdentityQueryService doctors;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    private final UUID visitId = UUID.randomUUID(), accountId = UUID.randomUUID(), doctorId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID(), roundId = UUID.randomUUID(), orderId = UUID.randomUUID();

    @BeforeEach void setUp() {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.of(doctorId));
        when(imaging.submit(any())).thenReturn(new SubmissionResult(round(), false));
    }

    @Test void exactEndpointEnforcesDoctorRoleAndAnonymousAuthentication() throws Exception {
        mvc.perform(request(body()).with(csrf())).andExpect(status().isUnauthorized());
        for (var role : AccountRole.values()) {
            if (role == AccountRole.DOCTOR) continue;
            mvc.perform(request(body()).with(csrf()).with(actor(role))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("MSG-40"));
        }
        verifyNoInteractions(imaging, doctors);
    }

    @Test void consentMustBeExplicitlyTrue() throws Exception {
        for (String payload : List.of(body().replace("\"patientConsentConfirmed\":true,", ""),
                body().replace("\"patientConsentConfirmed\":true", "\"patientConsentConfirmed\":false"),
                body().replace("\"patientConsentConfirmed\":true", "\"patientConsentConfirmed\":null"))) {
            mvc.perform(request(payload).with(csrf()).with(actor(AccountRole.DOCTOR))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(imaging);
    }

    @Test void receptionDispatchAndQueueDenyDoctorAndPatientAndAllowReceptionist() throws Exception {
        String dispatch = "/api/v1/reception/clinical-ordering-rounds/" + roundId + "/imaging-dispatch";
        when(workflow.dispatch(eq(roundId), any(), any())).thenReturn(List.of(UUID.randomUUID()));
        when(workflow.listReceptionRounds(any(), eq(0), eq(20))).thenReturn(new ClinicalRoundWorkflowService.RoundPage(0, 20, false, List.of()));
        for (var role : List.of(AccountRole.DOCTOR, AccountRole.PATIENT)) {
            mvc.perform(post(dispatch).contentType("application/json").content("{\"assignments\":[{\"serviceOrderId\":\"" + UUID.randomUUID() + "\",\"roomId\":\"" + UUID.randomUUID() + "\"}]}").with(csrf()).with(actor(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/reception/clinical-ordering-rounds").with(actor(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(workflow);
        mvc.perform(post(dispatch).contentType("application/json").content("{\"assignments\":[{\"serviceOrderId\":\"" + UUID.randomUUID() + "\",\"roomId\":\"" + UUID.randomUUID() + "\"}]}").with(csrf()).with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosticRequestIds.length()").value(1));
        mvc.perform(get("/api/v1/reception/clinical-ordering-rounds").with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isOk());
    }

    @Test void roomOptionsAreReceptionOnlyAndReturnMinimalRoomMetadata() throws Exception {
        UUID orderId = UUID.randomUUID(), roomId = UUID.randomUUID();
        var result = new ClinicalRoundWorkflowService.RoomOptions(orderId, List.of(
                new fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService.QueueRoom(roomId, "CDHA-01", "X-ray room")));
        when(workflow.eligibleImagingRooms(eq(List.of(orderId)), any())).thenReturn(List.of(result));
        String path = "/api/v1/reception/clinical-service-orders/" + orderId + "/eligible-imaging-rooms";
        for (var role : AccountRole.values()) if (role != AccountRole.RECEPTIONIST) {
            mvc.perform(get(path).with(actor(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get(path).with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceOrderId").value(orderId.toString()))
                .andExpect(jsonPath("$.items[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.items[0].technicianId").doesNotExist());
        mvc.perform(get("/api/v1/reception/clinical-service-orders/eligible-imaging-rooms")
                .param("serviceOrderIds", orderId.toString()).with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].serviceOrderId").value(orderId.toString()));
    }
    @Test void dispatchBodyRequiresRoomAndOrderIdentifiers() throws Exception {
        String path = "/api/v1/reception/clinical-ordering-rounds/" + roundId + "/imaging-dispatch";
        for (String payload : List.of("{}", "{\"assignments\":[]}", "{\"assignments\":[{\"serviceOrderId\":\"" + UUID.randomUUID() + "\"}]}")) {
            mvc.perform(post(path).contentType("application/json").content(payload).with(csrf()).with(actor(AccountRole.RECEPTIONIST)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(workflow);
    }

    @Test void missingDoctorProfileIsForbidden() throws Exception {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.empty());
        mvc.perform(request(body()).with(csrf()).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden());
        verifyNoInteractions(imaging);
    }

    @Test void missingKeyUsesSafeTransportValidation() throws Exception {
        mvc.perform(post(path()).contentType("application/json").content(body()).with(csrf()).with(actor(AccountRole.DOCTOR)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(imaging);
    }

    @Test void blankAndOversizedKeysAreRejectedBeforeSubmission() throws Exception {
        for (String key : List.of(" ", "k".repeat(129))) {
            mvc.perform(post(path()).header("Idempotency-Key", key).contentType("application/json").content(body())
                    .with(csrf()).with(actor(AccountRole.DOCTOR))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("MSG-39"));
        }
        verifyNoInteractions(imaging);
    }

    @Test void firstAndReplayHaveSameWhitelistedJsonAndDistinctHttpStatus() throws Exception {
        String correlation = UUID.randomUUID().toString();
        String first = mvc.perform(request(body()).with(csrf()).with(actor(AccountRole.DOCTOR)).header("X-Correlation-ID", correlation))
                .andExpect(status().isCreated()).andExpect(header().string("X-Correlation-ID", correlation))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(roundId.toString()))
                .andExpect(jsonPath("$.visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.roundNumber").value(2))
                .andExpect(jsonPath("$.orderedAt").value("2026-11-04T10:30:00Z"))
                .andExpect(jsonPath("$.orders[0].orderCode").value("SO-original"))
                .andExpect(jsonPath("$.orders[0].serviceType").value("IMAGING"))
                .andExpect(jsonPath("$.orders[0].priceSnapshot").value(250000))
                .andExpect(jsonPath("$.orders[0].paymentStatus").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        when(imaging.submit(any())).thenReturn(new SubmissionResult(round(), true));
        String replay = mvc.perform(request(body()).with(csrf()).with(actor(AccountRole.DOCTOR)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(first, replay);
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(first);
        assertEquals(Set.of("id", "visitId", "roundNumber", "orderedAt", "orders"), json.propertyNames());
        assertEquals(Set.of("orderId", "orderCode", "serviceId", "serviceName", "serviceType", "clinicalReason", "bodyArea",
                "instructions", "priceSnapshot", "orderStatus", "paymentStatus"), json.get("orders").get(0).propertyNames());
    }

    @Test void spoofedBodyIdentitiesAndStatusesCannotOverrideAuthenticatedIdentityOrPath() throws Exception {
        String payload = body().replaceFirst("\\{", "{\"doctorId\":\"" + UUID.randomUUID() + "\",\"doctorProfileId\":\""
                + UUID.randomUUID() + "\",\"accountId\":\"" + UUID.randomUUID() + "\",\"patientId\":\""
                + UUID.randomUUID() + "\",\"visitId\":\"" + UUID.randomUUID() + "\",\"roundNumber\":999,")
                .replace("\"serviceId\"", "\"paymentStatus\":\"PAID\",\"orderStatus\":\"CANCELLED\",\"serviceId\"");
        mvc.perform(request(payload).with(csrf()).with(actor(AccountRole.DOCTOR))).andExpect(status().isCreated());
        verify(imaging).submit(new ImagingRequestCommand(visitId, doctorId, "key", List.of(
                new ImagingOrderRequest(serviceId, "Suspected injury", "RIGHT_KNEE", "Weight-bearing")), true));
        verify(doctors).findDoctorProfileId(accountId);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"orders\":[]}", "{\"orders\":[null]}", "{",
            "{\"orders\":\"IMAGING\"}", "{\"orders\":[{\"serviceId\":\"not-a-uuid\"}]}",
            "{\"orders\":[{\"clinicalReason\":\" \"}]}"})
    void malformedOrIncompleteBodyIsRejected(String payload) throws Exception {
        mvc.perform(request(payload).with(csrf()).with(actor(AccountRole.DOCTOR))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(imaging);
    }

    @Test void invalidVisitUuidAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(post("/api/v1/visits/not-a-uuid/imaging-requests").header("Idempotency-Key", "key")
                .contentType("application/json").content(body()).with(csrf()).with(actor(AccountRole.DOCTOR)))
                .andExpect(status().isBadRequest());
        mvc.perform(request(body()).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden());
        verifyNoInteractions(imaging);
    }

    @ParameterizedTest @CsvSource({"MSG-39,400", "MSG-40,403", "MSG-41,404", "MSG-42,409", "IDEMPOTENCY_CONFLICT,409", "DEPENDENCY_UNAVAILABLE,503"})
    void applicationErrorsUseScopedSafeEnvelope(String code, int http) throws Exception {
        when(imaging.submit(any())).thenThrow(new ClinicalOperationException(code));
        mvc.perform(request(body()).with(csrf()).with(actor(AccountRole.DOCTOR))).andExpect(status().is(http))
                .andExpect(jsonPath("$.error.code").value(code)).andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test void nonImagingDomainRejectionIsBadRequestAndNoDataIsExposed() throws Exception {
        when(imaging.submit(any())).thenThrow(new ClinicalOperationException("MSG-39"));
        mvc.perform(request(body()).with(csrf()).with(actor(AccountRole.DOCTOR))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-39")).andExpect(jsonPath("$.orders").doesNotExist());
    }

    private String path() { return "/api/v1/visits/" + visitId + "/imaging-requests"; }
    private String body() { return "{\"patientConsentConfirmed\":true,\"orders\":[{\"serviceId\":\"" + serviceId + "\",\"clinicalReason\":\"Suspected injury\","
            + "\"bodyArea\":\"RIGHT_KNEE\",\"instructions\":\"Weight-bearing\"}]}"; }
    private MockHttpServletRequestBuilder request(String body) {
        return post(path()).header("Idempotency-Key", "key").contentType("application/json").content(body);
    }
    private RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(accountId, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    private ClinicalOrderingService.RoundView round() {
        return new ClinicalOrderingService.RoundView(roundId, visitId, 2, doctorId, Instant.parse("2026-11-04T10:30:00Z"),
                List.of(new ClinicalOrderingService.OrderView(orderId, "SO-original", visitId, UUID.randomUUID(), roundId,
                        serviceId, "X-ray Knee", ClinicalServiceType.IMAGING, doctorId, "Suspected injury", "RIGHT_KNEE", "Weight-bearing",
                        new BigDecimal("250000.00"), ServiceOrderStatus.ORDERED, ServiceOrderPaymentStatus.PENDING_PAYMENT)));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
