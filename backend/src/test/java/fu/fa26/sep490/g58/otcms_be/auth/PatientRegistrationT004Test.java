package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.PatientRegistrationController;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.RegistrationResponse;
import fu.fa26.sep490.g58.otcms_be.auth.exception.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.auth.service.RegistrationService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.RegistrationServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Focused evidence for feat-001-auth-login/T004. Production code is deliberately not changed by this test. */
class PatientRegistrationT004Test {
    @Test
    void registrationSuccessUses202AndDoesNotReturnSecret() throws Exception {
        RegistrationService service = mock(RegistrationService.class);
        when(service.register(any(), anyString())).thenReturn(new RegistrationResponse("If registration can be completed, a verification code will be sent."));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PatientRegistrationController(service)).build();

        mvc.perform(post("/api/auth/patient/register").contentType("application/json")
                        .content("{\"fullName\":\"Alice Patient\",\"email\":\"alice@example.com\",\"password\":\"pass1234\",\"confirmPassword\":\"pass1234\",\"patientConsent\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(not(containsString("accountId"))))
                .andExpect(content().string(not(containsString("status"))))
                .andExpect(content().string(not(containsString("pass1234"))));
    }

    @Test
    void controllerMapsRegistrationErrorsToSafeEnvelope() throws Exception {
        RegistrationService service = mock(RegistrationService.class);
        when(service.register(any(), anyString())).thenThrow(new RegistrationException("EMAIL_DELIVERY_UNAVAILABLE"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PatientRegistrationController(service)).build();

        mvc.perform(post("/api/auth/patient/register").contentType("application/json")
                        .content("{\"fullName\":\"Alice Patient\",\"email\":\"alice@example.com\",\"password\":\"pass1234\",\"confirmPassword\":\"pass1234\",\"patientConsent\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DELIVERY_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("Authentication request could not be completed"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void rateLimitIdentifierIsDeterministicHmacAndNeverRawIp() throws Exception {
        AuthProperties p = new AuthProperties(null, new AuthProperties.RateLimit(5, 3600), new AuthProperties.RateLimit(3, 3600), null, null, null, "v1", "v1", "test-secret");
        RegistrationServiceImpl service = new RegistrationServiceImpl(mock(AccountRepository.class), mock(ConsentEvidenceRepository.class), mock(EmailVerificationChallengeRepository.class), mock(AuthenticationAuditRepository.class), mock(EmailDeliveryService.class), p, mock(AuthConcurrencyRepository.class));
        Method method = RegistrationServiceImpl.class.getDeclaredMethod("hmacIdentifier", String.class);
        method.setAccessible(true);
        String rawIp = "203.0.113.7";
        String actual = (String) method.invoke(service, rawIp);
        assertNotEquals(rawIp, actual);
        assertEquals(64, actual.length());
        assertEquals(actual, method.invoke(service, rawIp));
        assertEquals(64, MessageDigest.getInstance("SHA-256").digest(actual.getBytes(StandardCharsets.UTF_8)).length * 2, "identifier remains a fixed-length digest token");
    }

    @Test
    void normalizationCanonicalizesEmail() throws Exception {
        RegistrationServiceImpl service = service();
        Method email = RegistrationServiceImpl.class.getDeclaredMethod("normalizeEmail", String.class);
        email.setAccessible(true);
        assertEquals("alice@example.com", email.invoke(service, "  Alice@Example.COM "));
    }

    @Test
    void registrationAcceptsEightToElevenCharacterLetterAndDigitPassword() {
        AccountRepository accounts = mock(AccountRepository.class);
        ConsentEvidenceRepository consents = mock(ConsentEvidenceRepository.class);
        EmailVerificationChallengeRepository challenges = mock(EmailVerificationChallengeRepository.class);
        AuthenticationAuditRepository audits = mock(AuthenticationAuditRepository.class);
        EmailDeliveryService mail = mock(EmailDeliveryService.class);
        AuthConcurrencyRepository concurrency = mock(AuthConcurrencyRepository.class);
        when(audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), any()))
                .thenReturn(0L);
        when(accounts.findByNormalizedEmail("alice@example.com")).thenReturn(java.util.Optional.empty());
        when(accounts.insertIfEmailAvailable(any(), eq("alice@example.com"), eq("PATIENT"),
                eq("PENDING_VERIFICATION"), anyString(), any(), any())).thenReturn(1);
        AccountJpaEntity saved = new AccountJpaEntity();
        saved.setId(UUID.randomUUID());
        when(accounts.findById(any())).thenReturn(java.util.Optional.of(saved));
        RegistrationServiceImpl service = new RegistrationServiceImpl(accounts, consents, challenges,
                audits, mail, properties(), concurrency);

        service.register(new fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest(
                "Alice Patient", " Alice@Example.com ", "abc12345", "abc12345", true), "198.51.100.8");

        verify(accounts).insertIfEmailAvailable(any(), eq("alice@example.com"), eq("PATIENT"),
                eq("PENDING_VERIFICATION"), argThat(hash -> !hash.contains("abc12345")), any(), any());
        verify(mail).sendVerificationCode(eq("alice@example.com"), matches("\\d{6}"), eq(saved.getId()));
    }

    @Test
    void registrationRejectsPasswordWithoutLetterOrDigitBeforePersistence() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuthenticationAuditRepository audits = mock(AuthenticationAuditRepository.class);
        when(audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), any()))
                .thenReturn(0L);
        when(accounts.findByNormalizedEmail(anyString())).thenReturn(java.util.Optional.empty());
        RegistrationServiceImpl service = new RegistrationServiceImpl(accounts, mock(ConsentEvidenceRepository.class),
                mock(EmailVerificationChallengeRepository.class), audits, mock(EmailDeliveryService.class),
                properties(), mock(AuthConcurrencyRepository.class));

        assertThrows(RegistrationException.class, () -> service.register(
                new fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest(
                        "Alice Patient", "alice@example.com", "12345678", "12345678", true), "198.51.100.8"));
        assertThrows(RegistrationException.class, () -> service.register(
                new fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest(
                        "Alice Patient", "alice@example.com", "abcdefgh", "abcdefgh", true), "198.51.100.9"));
        verify(accounts, never()).insertIfEmailAvailable(any(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void resendLocksAccountSupersedesCurrentChallengeAndSendsNewOtp() {
        AccountRepository accounts = mock(AccountRepository.class);
        EmailVerificationChallengeRepository challenges = mock(EmailVerificationChallengeRepository.class);
        EmailDeliveryService mail = mock(EmailDeliveryService.class);
        RegistrationServiceImpl service = new RegistrationServiceImpl(accounts,
                mock(ConsentEvidenceRepository.class), challenges, mock(AuthenticationAuditRepository.class), mail,
                properties(), mock(AuthConcurrencyRepository.class));
        UUID id = UUID.randomUUID(); AccountJpaEntity account = new AccountJpaEntity(); account.setId(id);
        account.setNormalizedEmail("alice@example.com"); account.setAccountType(AccountType.PATIENT); account.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(accounts.findByNormalizedEmail("alice@example.com")).thenReturn(java.util.Optional.of(account));
        when(accounts.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(account));
        when(challenges.countByAccountIdAndCreatedAtAfter(eq(id), any())).thenReturn(0L);
        service.resendOtp(" Alice@Example.com ");
        verify(accounts).findByIdForUpdate(id);
        verify(challenges).supersedeCurrent(eq(id), any());
        verify(challenges).save(any());
        verify(mail).sendVerificationCode(eq("alice@example.com"), anyString(), eq(id));
    }

    @Test
    void resendEmailFailurePersistsAuditAndPropagatesSafeCode() {
        AccountRepository accounts = mock(AccountRepository.class);
        EmailVerificationChallengeRepository challenges = mock(EmailVerificationChallengeRepository.class);
        AuthenticationAuditRepository audits = mock(AuthenticationAuditRepository.class);
        EmailDeliveryService mail = mock(EmailDeliveryService.class);
        RegistrationServiceImpl service = new RegistrationServiceImpl(accounts,
                mock(ConsentEvidenceRepository.class), challenges, audits, mail, properties(), mock(AuthConcurrencyRepository.class));
        UUID id = UUID.randomUUID(); AccountJpaEntity account = new AccountJpaEntity(); account.setId(id);
        account.setNormalizedEmail("alice@example.com"); account.setAccountType(AccountType.PATIENT); account.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(accounts.findByNormalizedEmail("alice@example.com")).thenReturn(java.util.Optional.of(account));
        when(accounts.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(account));
        when(challenges.countByAccountIdAndCreatedAtAfter(eq(id), any())).thenReturn(0L);
        doThrow(new RuntimeException("smtp down")).when(mail).sendVerificationCode(eq("alice@example.com"), anyString(), eq(id));
        RegistrationException ex = assertThrows(RegistrationException.class, () -> service.resendOtp("alice@example.com"));
        assertEquals("EMAIL_DELIVERY_UNAVAILABLE", ex.getMessage());
        verify(audits).save(argThat(a -> "RESEND_EMAIL_DELIVERY_UNAVAILABLE".equals(a.getOutcome())));
    }

    private RegistrationServiceImpl service() {
        return new RegistrationServiceImpl(mock(AccountRepository.class),
                mock(ConsentEvidenceRepository.class), mock(EmailVerificationChallengeRepository.class),
                mock(AuthenticationAuditRepository.class), mock(EmailDeliveryService.class), properties(), mock(AuthConcurrencyRepository.class));
    }

    private AuthProperties properties() {
        return new AuthProperties(null, new AuthProperties.RateLimit(5, 3600), new AuthProperties.RateLimit(3, 3600),
                null, null, null, "v1", "v1", "test-secret");
    }
}
