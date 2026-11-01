package fu.fa26.sep490.g58.otcms_be.notification;

import fu.fa26.sep490.g58.otcms_be.notification.controller.MyNotificationController;
import fu.fa26.sep490.g58.otcms_be.notification.exception.*;
import fu.fa26.sep490.g58.otcms_be.notification.model.dto.NotificationDtos.*;
import fu.fa26.sep490.g58.otcms_be.notification.service.MyNotificationService;
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
import org.junit.jupiter.params.provider.EnumSource;
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

@WebMvcTest(MyNotificationController.class)
@Import({SecurityConfig.class, NotificationExceptionHandler.class, NotificationControllerTest.Fixture.class})
class NotificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MyNotificationService notifications;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final AuthenticatedAccount current = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, UUID.randomUUID());
    final String base = "/api/v1/me/notifications";
    @Test void anonymousIsRejected() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized()); verifyNoInteractions(notifications);
    }
    @Test void ignoresForgedRecipientAndUsesPrincipalForEveryEndpoint() throws Exception {
        when(notifications.list(current, true, 0, 20)).thenReturn(new NotificationPage(List.of(), 0, 20, 0, 1));
        when(notifications.unreadCount(current)).thenReturn(new UnreadCount(0));
        when(notifications.readAll(current)).thenReturn(new ReadAllResult(3));
        mvc.perform(get(base).with(actor()).param("unread", "true").param("recipientAccountId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray()).andExpect(jsonPath("$.totalItems").value(0));
        verify(notifications).list(current, true, 0, 20);
        mvc.perform(get(base + "/unread-count").with(actor())).andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(post(base + "/read-all").with(actor()).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(3));
        verify(notifications).readAll(current);
    }
    @Test void missingAndForeignReturnSame404Envelope() throws Exception {
        when(notifications.markRead(eq(current), any())).thenThrow(new NotificationNotFoundException());
        for (int i = 0; i < 2; i++)
            mvc.perform(post(base + "/" + UUID.randomUUID() + "/read").with(actor()).with(csrf()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"))
                    .andExpect(jsonPath("$.code").doesNotExist()).andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
    @Test void readIs200AndUnexpectedFailureUsesMsg04() throws Exception {
        UUID id = UUID.randomUUID();
        var item = new Item(id, "Title", "Content", java.time.OffsetDateTime.now(), true, null, List.of());
        when(notifications.markRead(current, id)).thenReturn(item);
        for (int i = 0; i < 2; i++)
            mvc.perform(post(base + "/" + id + "/read").with(actor()).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true)).andExpect(jsonPath("$.allowedActions").isEmpty());
        when(notifications.unreadCount(current)).thenThrow(new IllegalStateException("private"));
        mvc.perform(get(base + "/unread-count").with(actor())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("MSG-04"));
    }
    @ParameterizedTest @EnumSource(AccountRole.class)
    void everyAuthenticatedRoleCanUseAllFourEndpoints(AccountRole role) throws Exception {
        var principal = new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID());
        var identity = authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        UUID id = UUID.randomUUID();
        when(notifications.list(principal, false, 0, 20)).thenReturn(new NotificationPage(List.of(), 0, 20, 0, 1));
        when(notifications.unreadCount(principal)).thenReturn(new UnreadCount(0));
        when(notifications.markRead(principal, id)).thenReturn(new Item(id, "Title", "Content",
                java.time.OffsetDateTime.parse("2026-11-02T08:00:00+07:00"), true, null, List.of()));
        when(notifications.readAll(principal)).thenReturn(new ReadAllResult(0));
        for (String endpoint : List.of("list", "count", "read", "readAll"))
            mvc.perform(notificationRequest(endpoint, id).with(identity).with(csrf())).andExpect(status().isOk());
        verify(notifications).list(principal, false, 0, 20);
        verify(notifications).unreadCount(principal);
        verify(notifications).markRead(principal, id);
        verify(notifications).readAll(principal);
        verifyNoMoreInteractions(notifications);
    }
    @ParameterizedTest @ValueSource(strings = {"list", "count", "read", "readAll"})
    void allFourEndpointsRejectAnonymous(String endpoint) throws Exception {
        mvc.perform(notificationRequest(endpoint, UUID.randomUUID()).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder notificationRequest(String endpoint, UUID id) {
        return switch (endpoint) {
            case "list" -> get(base);
            case "count" -> get(base + "/unread-count");
            case "read" -> post(base + "/" + id + "/read");
            case "readAll" -> post(base + "/read-all");
            default -> throw new IllegalArgumentException(endpoint);
        };
    }
    private RequestPostProcessor actor() {
        return authentication(new UsernamePasswordAuthenticationToken(current, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
