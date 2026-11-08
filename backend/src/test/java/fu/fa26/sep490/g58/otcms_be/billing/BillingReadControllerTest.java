package fu.fa26.sep490.g58.otcms_be.billing;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.billing.controller.BillingReadController;
import fu.fa26.sep490.g58.otcms_be.billing.exception.*;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.*;
import fu.fa26.sep490.g58.otcms_be.config.*;
import java.util.*;
import org.junit.jupiter.api.*;
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

@WebMvcTest(BillingReadController.class)
@Import({SecurityConfig.class, BillingExceptionHandler.class, BillingReadApiExceptionHandler.class, CorrelationIdFilter.class, BillingReadControllerTest.Fixture.class})
class BillingReadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BillingReadService reads;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(UUID.randomUUID(), role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @Test void anonymousAndUnrelatedRolesDenied() throws Exception {
        mvc.perform(get("/api/v1/payments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments").with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invoices/unpaid").with(actor(AccountRole.PATIENT))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/" + UUID.randomUUID() + "/receipt").with(actor(AccountRole.ADMIN))).andExpect(status().isForbidden());
        verifyNoInteractions(reads);
    }
    @Test void patientHistoryAndStaffUnpaidPaginationArePrivate() throws Exception {
        when(reads.payments(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new BillingReadService.ResultPage<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/payments").with(actor(AccountRole.PATIENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalItems").value(0)).andExpect(header().string("Cache-Control", "no-store"));
        when(reads.unpaid(any(), eq(0), eq(20))).thenReturn(new BillingReadService.ResultPage<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/invoices/unpaid").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
    }
    @Test void receiptIsAnAuthenticatedUtf8AttachmentAndBusinessErrorsUseEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(reads.receiptExport(any(), eq(id))).thenReturn("PAYMENT RECEIPT\nAmount paid: 30000 VND\n");
        mvc.perform(get("/api/v1/payments/" + id + "/receipt").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentType("text/plain;charset=UTF-8"));
        when(reads.receiptExport(any(), eq(id))).thenThrow(new BillingException("MSG-42"));
        mvc.perform(get("/api/v1/payments/" + id + "/receipt").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-42"));
    }
    @Test void malformedDatesAndIdentifiersFailBeforeService() throws Exception {
        mvc.perform(get("/api/v1/payments?from=bad-date").with(actor(AccountRole.PATIENT)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-39"));
        mvc.perform(get("/api/v1/invoices/bad-id/print").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-39"));
        verifyNoInteractions(reads);
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
