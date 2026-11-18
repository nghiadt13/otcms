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
import fu.fa26.sep490.g58.otcms_be.billing.controller.BillingDetailController;
import fu.fa26.sep490.g58.otcms_be.billing.exception.*;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingDetailService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService.ResultPage;
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

@WebMvcTest(BillingDetailController.class)
@Import({SecurityConfig.class, BillingExceptionHandler.class, BillingReadApiExceptionHandler.class, CorrelationIdFilter.class, BillingDetailControllerTest.Fixture.class})
class BillingDetailControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BillingDetailService details;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService accounts;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(UUID.randomUUID(), role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @Test void anonymousAndWrongRolesAreDeniedBeforeTheService() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/payments/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments/" + id).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/" + id + "/receipt").with(actor(AccountRole.ADMIN))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/" + id + "/receipt.pdf").with(actor(AccountRole.PATIENT))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invoice-items/unpaid").with(actor(AccountRole.PATIENT))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reception/patients/" + id + "/billing").with(actor(AccountRole.PATIENT))).andExpect(status().isForbidden());
        verifyNoInteractions(details);
    }
    @Test void unpaidItemsAreStaffOnlyNoStoreAndCriteriaReachTheService() throws Exception {
        when(details.unpaidItems(any(), any(), any(), any(), any(), eq(2), eq(5))).thenReturn(new ResultPage<>(List.of(), 2, 5, 0, 0));
        mvc.perform(get("/api/v1/invoice-items/unpaid?page=2&size=5&from=2026-11-01&to=2026-11-30").with(actor(AccountRole.OWNER)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/v1/invoice-items/unpaid?from=bad-date").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-54"));
    }
    @Test void businessErrorsUseTheEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(details.payment(any(), eq(id))).thenThrow(new BillingException("MSG-50"));
        mvc.perform(get("/api/v1/payments/" + id).with(actor(AccountRole.PATIENT)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));
        when(details.receiptPdf(any(), eq(id))).thenThrow(new BillingException("MSG-04"));
        mvc.perform(get("/api/v1/payments/" + id + "/receipt.pdf").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("MSG-04"));
    }
    @Test void receiptPdfIsAnInlineNoStorePdf() throws Exception {
        UUID id = UUID.randomUUID();
        when(details.receiptPdf(any(), eq(id))).thenReturn("%PDF-1.7".getBytes());
        mvc.perform(get("/api/v1/payments/" + id + "/receipt.pdf").with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().contentType("application/pdf"));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
