package fu.fa26.sep490.g58.otcms_be.review;

import fu.fa26.sep490.g58.otcms_be.review.controller.*;
import fu.fa26.sep490.g58.otcms_be.review.exception.*;
import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.*;
import fu.fa26.sep490.g58.otcms_be.review.service.ReviewService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.*;
import java.util.*;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PatientReviewController.class, AdminReviewController.class, PublicReviewController.class})
@Import({SecurityConfig.class, ReviewApiExceptionHandler.class, ReviewControllerTest.Fixture.class})
class ReviewControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReviewService reviews;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    @Test void publicAccessibleWithoutLoginAndSafeShape() throws Exception {
        when(reviews.published(0, 20, null)).thenReturn(new PublicReviewPage(List.of(
                new PublicReview(UUID.randomUUID(), 5, "A review text",
                        java.time.OffsetDateTime.parse("2026-11-01T19:00:00+07:00"), "Clinic reply")),
                0, 20, 1, 1, new Summary(5, 1)));
        var response = mvc.perform(get("/api/v1/public/reviews")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].text").value("A review text"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-01T19:00:00+07:00"))
                .andExpect(jsonPath("$.items[0].clinicReply").value("Clinic reply"))
                .andExpect(jsonPath("$.items[0].patient").doesNotExist())
                .andExpect(jsonPath("$.items[0].patientName").doesNotExist())
                .andExpect(jsonPath("$.items[0].fullName").doesNotExist())
                .andExpect(jsonPath("$.items[0].patientProfileId").doesNotExist())
                .andExpect(jsonPath("$.items[0].authorAccountId").doesNotExist())
                .andExpect(jsonPath("$.items[0].visitId").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var items = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("items");
        for (var item : items) org.junit.jupiter.api.Assertions.assertEquals(
                Set.of("id", "rating", "text", "createdAt", "clinicReply"), item.propertyNames());
        mvc.perform(get("/api/v1/admin/reviews")).andExpect(status().isUnauthorized());
    }
    @Test void roleBoundariesAndAnonymousRejected() throws Exception {
        mvc.perform(get("/api/v1/me/reviews")).andExpect(status().isUnauthorized());
        for (AccountRole role : AccountRole.values()) {
            if (role != AccountRole.ADMIN)
                mvc.perform(get("/api/v1/admin/reviews").with(actor(role))).andExpect(status().isForbidden());
            if (role != AccountRole.PATIENT)
                mvc.perform(get("/api/v1/me/reviews").with(actor(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reviews);
    }
    @Test void allPatientReviewEndpointsEnforcePatientRole() throws Exception {
        var created = new MyReview(UUID.randomUUID(), UUID.randomUUID(), java.time.LocalDate.of(2026, 11, 1),
                5, "A valid review", fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus.PENDING,
                null, java.time.OffsetDateTime.now(), new Patient("Patient", "SELF"));
        when(reviews.eligible(any())).thenReturn(new Items<>(List.of()));
        when(reviews.mine(any())).thenReturn(new Items<>(List.of()));
        when(reviews.submit(any(), any())).thenReturn(created);
        for (String path : List.of("/api/v1/me/reviews/eligible-visits", "/api/v1/me/reviews", "/api/v1/reviews")) {
            boolean submitting = path.equals("/api/v1/reviews");
            mvc.perform(patientRequest(path).with(csrf())).andExpect(status().isUnauthorized());
            for (AccountRole role : AccountRole.values()) {
                var result = mvc.perform(patientRequest(path).with(csrf()).with(actor(role)));
                if (role == AccountRole.PATIENT)
                    result.andExpect(submitting ? status().isCreated() : status().isOk());
                else result.andExpect(status().isForbidden());
            }
        }
        verify(reviews, times(1)).eligible(any());
        verify(reviews, times(1)).mine(any());
        verify(reviews, times(1)).submit(any(), any());
        verifyNoMoreInteractions(reviews);
    }
    @Test void clientAccountIdQueryCannotChangeEitherGetScope() throws Exception {
        UUID currentAccount = UUID.randomUUID(), otherAccount = UUID.randomUUID();
        var identity = authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccount(currentAccount, AccountRole.PATIENT, UUID.randomUUID()), null,
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
        when(reviews.eligible(currentAccount)).thenReturn(new Items<>(List.of()));
        when(reviews.mine(currentAccount)).thenReturn(new Items<>(List.of()));
        for (String path : List.of("/api/v1/me/reviews/eligible-visits", "/api/v1/me/reviews"))
            mvc.perform(get(path).with(identity).param("accountId", otherAccount.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        verify(reviews).eligible(currentAccount);
        verify(reviews).mine(currentAccount);
        verifyNoMoreInteractions(reviews);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patientRequest(String path) {
        if (!path.equals("/api/v1/reviews")) return get(path);
        return post(path).contentType("application/json").content("{\"visitId\":\"" + UUID.randomUUID()
                + "\",\"rating\":5,\"text\":\"A valid review\"}");
    }
    @ParameterizedTest
    @ValueSource(strings = {"status=INVALID", "rating=0", "rating=6", "replied=invalid", "from=bad-date",
            "to=bad-date", "from=2026-11-02&to=2026-11-01", "sort=unsupported,desc"})
    void invalidAdminFiltersReturnMsg54(String query) throws Exception {
        var real = new fu.fa26.sep490.g58.otcms_be.review.service.impl.ReviewServiceImpl(
                mock(fu.fa26.sep490.g58.otcms_be.review.repository.PatientReviewRepository.class),
                mock(fu.fa26.sep490.g58.otcms_be.review.repository.PatientReviewHistoryRepository.class),
                mock(fu.fa26.sep490.g58.otcms_be.review.service.OwnPatientProfileService.class),
                mock(fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.class),
                mock(fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService.class),
                java.time.Clock.systemUTC(), source -> source,
                mock(fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher.class));
        when(reviews.list(any(), any(), any(), any(), any(), anyInt(), anyInt(), any())).thenAnswer(i ->
                real.list(i.getArgument(0), i.getArgument(1), i.getArgument(2), i.getArgument(3),
                        i.getArgument(4), i.getArgument(5), i.getArgument(6), i.getArgument(7)));
        mvc.perform(get("/api/v1/admin/reviews?" + query).with(actor(AccountRole.ADMIN)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-54"));
    }
    @ParameterizedTest @ValueSource(strings = {"list", "reply"})
    void representativeAdminEndpointsEnforceRole(String operation) throws Exception {
        String path = "/api/v1/admin/reviews" + (operation.equals("reply") ? "/" + UUID.randomUUID() + "/reply" : "");
        java.util.function.Supplier<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> request = () ->
                operation.equals("reply") ? put(path).contentType("application/json").content("{\"text\":\"Clinic reply\"}") : get(path);
        mvc.perform(request.get().with(csrf())).andExpect(status().isUnauthorized());
        for (AccountRole role : AccountRole.values()) {
            var result = mvc.perform(request.get().with(csrf()).with(actor(role)));
            if (role == AccountRole.ADMIN) result.andExpect(status().isOk());
            else result.andExpect(status().isForbidden());
        }
        if (operation.equals("reply")) verify(reviews).reply(any(), any(), eq(new ReplyRequest("Clinic reply")));
        else verify(reviews).list(null, null, null, null, null, 0, 20, null);
        verifyNoMoreInteractions(reviews);
    }
    @Test void rootFieldErrorsAndStrictIntegerRating() throws Exception {
        for (String rating : List.of("1.5", "\"5\"", "2147483648"))
            mvc.perform(post("/api/v1/reviews").with(actor(AccountRole.PATIENT)).with(csrf())
                    .contentType("application/json").content("{\"visitId\":\"" + UUID.randomUUID()
                            + "\",\"rating\":" + rating + ",\"text\":\"A valid review\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-57"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
        when(reviews.submit(any(), any())).thenThrow(new ReviewOperationException(400, "MSG-57",
                List.of(new FieldError("text", "MSG-57"))));
        var response = mvc.perform(post("/api/v1/reviews").with(actor(AccountRole.PATIENT)).with(csrf())
                .contentType("application/json").content("{\"visitId\":\"" + UUID.randomUUID()
                        + "\",\"rating\":5,\"text\":\"  short  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("text"))
                .andExpect(jsonPath("$.error.code").value("MSG-57"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/reviews-error-response.json"), response);
    }
    @Test void optimisticLockConflictAndUnexpectedFailure() throws Exception {
        UUID id = UUID.randomUUID();
        when(reviews.decide(eq(id), any(), eq("APPROVE")))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("review", id));
        mvc.perform(post("/api/v1/admin/reviews/" + id + "/approve").with(actor(AccountRole.ADMIN)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-56"));
        when(reviews.detail(id)).thenThrow(new IllegalStateException("private infrastructure detail"));
        mvc.perform(get("/api/v1/admin/reviews/" + id).with(actor(AccountRole.ADMIN)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error.code").value("MSG-04"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private infrastructure"))));
    }
    @Test void routesAllReviewOperationsAndReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        var created = new MyReview(id, UUID.randomUUID(), java.time.LocalDate.of(2026, 11, 1), 5, "A valid review",
                fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus.PENDING, null,
                java.time.OffsetDateTime.now(), new Patient("Private patient", "SELF"));
        when(reviews.submit(any(), any())).thenReturn(created);
        mvc.perform(post("/api/v1/reviews").with(actor(AccountRole.PATIENT)).with(csrf())
                .contentType("application/json").content("{\"visitId\":\"" + created.visitId()
                        + "\",\"rating\":5,\"text\":\"A valid review\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("A valid review"))
                .andExpect(jsonPath("$.content").doesNotExist()).andExpect(jsonPath("$.status").value("PENDING"));
        when(reviews.eligible(any())).thenReturn(new Items<>(List.of()));
        when(reviews.mine(any())).thenReturn(new Items<>(List.of(created)));
        mvc.perform(get("/api/v1/me/reviews/eligible-visits").with(actor(AccountRole.PATIENT))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/reviews").with(actor(AccountRole.PATIENT))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/reviews/" + id).with(actor(AccountRole.ADMIN))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/reviews").with(actor(AccountRole.ADMIN))
                .param("status", "HIDDEN").param("rating", "5").param("replied", "false")
                .param("from", "2026-11-01").param("to", "2026-11-02").param("page", "1").param("size", "10")
                .param("sort", "submittedAt,desc")).andExpect(status().isOk());
        verify(reviews).list("HIDDEN", 5, false, java.time.LocalDate.of(2026, 11, 1),
                java.time.LocalDate.of(2026, 11, 2), 1, 10, "submittedAt,desc");
        for (String action : List.of("approve", "hide", "unhide")) {
            mvc.perform(post("/api/v1/admin/reviews/" + id + "/" + action).with(actor(AccountRole.ADMIN)).with(csrf()))
                    .andExpect(status().isOk());
            verify(reviews).decide(eq(id), any(), eq(action.toUpperCase(java.util.Locale.ROOT)));
        }
        mvc.perform(put("/api/v1/admin/reviews/" + id + "/reply").with(actor(AccountRole.ADMIN)).with(csrf())
                .contentType("application/json").content("{\"text\":\"Reply\"}")).andExpect(status().isOk());
        verify(reviews).reply(eq(id), any(), eq(new ReplyRequest("Reply")));
    }
    @Test void uniqueConstraintRaceMapsTo409() throws Exception {
        var sql = new java.sql.SQLException("duplicate review", "23505");
        var constraint = new org.hibernate.exception.ConstraintViolationException("duplicate", sql, "uk_patient_reviews_visit");
        when(reviews.submit(any(), any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate", constraint));
        mvc.perform(post("/api/v1/reviews").with(actor(AccountRole.PATIENT)).with(csrf())
                .contentType("application/json").content("{\"visitId\":\"" + UUID.randomUUID()
                        + "\",\"rating\":5,\"text\":\"A valid review\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-56"));
    }
    private RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(UUID.randomUUID(), role,
                UUID.randomUUID()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
