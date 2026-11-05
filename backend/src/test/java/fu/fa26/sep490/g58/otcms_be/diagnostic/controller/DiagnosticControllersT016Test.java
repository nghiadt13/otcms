package fu.fa26.sep490.g58.otcms_be.diagnostic.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.mapper.DiagnosticDtoMapper;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatusPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticFindingsService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticHistoryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRequestService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticStatusService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

class DiagnosticControllersT016Test {
    private final UUID actorId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final AuthenticatedAccount principal = new AuthenticatedAccount(actorId, AccountRole.DOCTOR,
            UUID.randomUUID());
    private final DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.DOCTOR);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void authenticateDoctor() {
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR")));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void diagnosticControllerDoesNotExposeCompetingPublicCreateHandler() throws Exception {
        DiagnosticRequestService requests = mock(DiagnosticRequestService.class);
        DiagnosticStatusService statuses = mock(DiagnosticStatusService.class);
        UUID serviceId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new DiagnosticRequestController(requests, statuses, new DiagnosticDtoMapper()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        mvc.perform(post("/api/v1/visits/{visitId}/imaging-requests", visitId)
                        .with(authentication(SecurityContextHolder.getContext().getAuthentication()))
                        .header("Idempotency-Key", "create-1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(new DiagnosticDtos.CreateRequest(
                                serviceId, "MRI", "Knee", "Pain", roomId, null))))
                .andExpect(status().isMethodNotAllowed());

        org.mockito.Mockito.verifyNoInteractions(requests, statuses);
    }

    @Test
    void historyControllerPassesActorScopeAndPaginationToService() throws Exception {
        DiagnosticHistoryService history = mock(DiagnosticHistoryService.class);
        UUID patientId = UUID.randomUUID();
        when(history.getImagingHistory(actor, patientId, visitId, 2, 10))
                .thenReturn(new DiagnosticHistoryPage(List.of(), 2, 10, 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new DiagnosticHistoryController(history, new DiagnosticDtoMapper()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        mvc.perform(get("/api/v1/imaging-history").with(authentication(SecurityContextHolder.getContext().getAuthentication()))
                        .param("patientId", patientId.toString()).param("visitId", visitId.toString())
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));

        verify(history).getImagingHistory(actor, patientId, visitId, 2, 10);
    }

    @Test
    void statusControllerPassesAuthenticatedActorToStatusService() throws Exception {
        DiagnosticRequestService requests = mock(DiagnosticRequestService.class);
        DiagnosticStatusService statuses = mock(DiagnosticStatusService.class);
        UUID requestId = UUID.randomUUID();
        when(statuses.listVisitStatus(actor, visitId, 0, 20))
                .thenReturn(new DiagnosticOperationalStatusPage(List.of(), 0, 20, 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new DiagnosticRequestController(requests, statuses, new DiagnosticDtoMapper()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        mvc.perform(get("/api/v1/visits/{visitId}/imaging-requests", visitId)
                        .with(authentication(SecurityContextHolder.getContext().getAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(statuses).listVisitStatus(actor, visitId, 0, 20);
    }

    @Test
    void findingsControllerPassesReviewCommandToServiceAndMapsRequest() throws Exception {
        DiagnosticFindingsService findings = mock(DiagnosticFindingsService.class);
        UUID requestId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        String studyRef = "study-123";
        var view = new DiagnosticRequestView(requestId, visitId, UUID.randomUUID(), actorId,
                serviceId, roomId, "MRI", "Knee", "Pain", DiagnosticRequestStatus.RESULT_AVAILABLE,
                null, null, studyRef, 2, Instant.parse("2026-11-04T10:00:00Z"));
        when(findings.review(actor, requestId, "review-1", 2, studyRef, "unknown")).thenReturn(view);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new DiagnosticFindingsController(findings, new DiagnosticDtoMapper()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        mvc.perform(post("/api/v1/imaging-requests/{requestId}/reviews", requestId)
                        .with(authentication(SecurityContextHolder.getContext().getAuthentication()))
                        .header("Idempotency-Key", "review-1").header("X-Expected-Version", "2")
                        .contentType("application/json").content("{\"studyRef\":\"" + studyRef + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.currentStudyRef").value(studyRef))
                .andExpect(jsonPath("$.version").value(2));

        verify(findings).review(actor, requestId, "review-1", 2, studyRef, "unknown");
    }
}
