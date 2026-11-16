package fu.fa26.sep490.g58.otcms_be.review;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.*;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.review.controller.*;
import fu.fa26.sep490.g58.otcms_be.review.exception.ReviewApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.*;
import fu.fa26.sep490.g58.otcms_be.review.model.entity.PatientReviewJpaEntity;
import fu.fa26.sep490.g58.otcms_be.review.repository.*;
import fu.fa26.sep490.g58.otcms_be.review.service.*;
import fu.fa26.sep490.g58.otcms_be.review.service.impl.ReviewServiceImpl;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real review application validation behind the HTTP boundary; persistence remains mocked here. */
@WebMvcTest({PatientReviewController.class, AdminReviewController.class})
@Import({SecurityConfig.class, ReviewApiExceptionHandler.class, ReviewTextValidationHttpTest.Fixture.class})
class ReviewTextValidationHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReviewService service;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final PatientReviewRepository reviews = mock(PatientReviewRepository.class);
    final AuditLogWriter history = mock(AuditLogWriter.class);
    final OwnPatientProfileService own = mock(OwnPatientProfileService.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final VisitReviewSourceService visits = mock(VisitReviewSourceService.class);
    final UUID account = UUID.randomUUID(), profileId = UUID.randomUUID(), visitId = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-01T12:00:00Z");
    final String emoji = new String(Character.toChars(0x1F600));
    UUID reviewId;
    @BeforeEach void application() {
        var profile = new PatientProfileDirectory.ProfileRecord(profileId, account, "Test patient", null, null, null, null);
        when(own.requireOwnProfile(any())).thenReturn(profile);
        when(profiles.findProfile(profileId)).thenReturn(Optional.of(profile));
        when(visits.find(visitId)).thenReturn(Optional.of(new VisitReviewSourceService.Source(visitId, profileId, "CLOSED", now, "Doctor", "Service")));
        var review = PatientReviewJpaEntity.submit(visitId, profileId, account, 5, "Valid review text", now);
        reviewId = review.getId(); when(reviews.findById(reviewId)).thenReturn(Optional.of(review));
        var real = new ReviewServiceImpl(reviews, history, own, profiles, visits, Clock.fixed(now, ZoneOffset.UTC),
                source -> source, mock(NotificationPublisher.class));
        when(service.submit(any(), any())).thenAnswer(invocation -> real.submit(invocation.getArgument(0), invocation.getArgument(1)));
        when(service.reply(any(), any(), any())).thenAnswer(invocation -> real.reply(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }
    @ParameterizedTest @ValueSource(strings = {"1234567890", "true", "{}", "[]"})
    void submitRejectsNonStringBeforeApplication(String value) throws Exception {
        invalid(submit("\"text\":" + value)); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"123", "true", "{}", "[]"})
    void replyRejectsNonStringBeforeApplication(String value) throws Exception {
        invalid(reply("\"text\":" + value)); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"null", "MISSING"})
    void submitNullOrMissingGoesThroughApplicationValidation(String value) throws Exception {
        invalid(submit(value.equals("MISSING") ? "" : "\"text\":null"));
        verify(service).submit(any(), any()); verifyNoInteractions(own, history);
    }
    @ParameterizedTest @ValueSource(strings = {"null", "MISSING"})
    void replyNullOrMissingGoesThroughApplicationValidation(String value) throws Exception {
        invalid(reply(value.equals("MISSING") ? "" : "\"text\":null"));
        verify(service).reply(any(), any(), any()); verifyNoInteractions(history);
    }
    @ParameterizedTest @CsvSource({"9,false", "10,true", "1000,true", "1001,false"})
    void reviewAsciiBoundariesAfterTrim(int count, boolean valid) throws Exception {
        String text = "x".repeat(count);
        var request = submit("\"text\":" + json("  " + text + "  "));
        if (valid) mvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value(text)).andExpect(jsonPath("$.status").value("PENDING"));
        else invalid(request);
    }
    @Test void clientAccountIdCannotChangeSubmitOwnership() throws Exception {
        UUID otherAccount = UUID.randomUUID(), otherProfile = UUID.randomUUID();
        when(visits.find(visitId)).thenReturn(Optional.of(new VisitReviewSourceService.Source(
                visitId, otherProfile, "CLOSED", now, "Doctor", "Service")));
        mvc.perform(submit("\"text\":\"Valid review text\",\"accountId\":\"" + otherAccount + "\""))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MSG-56"));
        verify(service).submit(eq(account), any());
        verify(own).requireOwnProfile(account);
        verify(own, never()).requireOwnProfile(otherAccount);
        verifyNoInteractions(reviews, history);
    }
    @ParameterizedTest @CsvSource({"5,false", "9,false", "10,true", "1000,true", "1001,false"})
    void reviewUnicodeCodePointBoundaries(int count, boolean valid) throws Exception {
        String text = emoji.repeat(count);
        var request = submit("\"text\":" + json("  " + text + "  "));
        if (valid) mvc.perform(request).andExpect(status().isCreated()).andExpect(jsonPath("$.text").value(text));
        else invalid(request);
    }
    @ParameterizedTest @CsvSource({"0,false", "1,true", "1000,true", "1001,false"})
    void replyUnicodeCodePointBoundaries(int count, boolean valid) throws Exception {
        String text = emoji.repeat(count);
        var request = reply("\"text\":" + json("  " + text + "  "));
        if (valid) mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.reply.text").value(text));
        else invalid(request);
    }
    @Test void validJsonStringsAreAcceptedAndTrimmed() throws Exception {
        mvc.perform(submit("\"text\":\"  Valid review text  \""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("Valid review text"));
        mvc.perform(reply("\"text\":\"  Valid reply  \""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reply.text").value("Valid reply"));
    }
    private void invalid(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-57"))
                .andExpect(jsonPath("$.code").doesNotExist()).andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("text"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG-57"));
    }
    private MockHttpServletRequestBuilder submit(String property) {
        return post("/api/v1/reviews").with(actor(AccountRole.PATIENT)).with(csrf()).contentType("application/json")
                .content("{\"visitId\":\"" + visitId + "\",\"rating\":5" + (property.isEmpty() ? "" : "," + property) + "}");
    }
    private MockHttpServletRequestBuilder reply(String property) {
        return put("/api/v1/admin/reviews/" + reviewId + "/reply").with(actor(AccountRole.ADMIN)).with(csrf())
                .contentType("application/json").content("{" + property + "}");
    }
    private RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, UUID.randomUUID()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    private String json(String text) { return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(text); }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
