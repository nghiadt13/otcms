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
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
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
        AuthProperties p = new AuthProperties(null, new AuthProperties.RateLimit(5, 3600), new AuthProperties.RateLimit(3, 3600), null, null, "v1", "v1", "test-secret");
        RegistrationServiceImpl service = new RegistrationServiceImpl(mock(AccountRepository.class), mock(VerificationCodeRepository.class), mock(AuditLogWriter.class), mock(AuditLogRepository.class), mock(EmailDeliveryService.class), p, mock(AuthConcurrencyRepository.class));
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"abcd1234", "ábcdefg١"})
    void registrationAcceptsEightToElevenCharacterLetterAndDigitPassword(String password) {
        AccountRepository accounts = mock(AccountRepository.class);
        VerificationCodeRepository challenges = mock(VerificationCodeRepository.class);
        AuditLogWriter auditWriter = mock(AuditLogWriter.class);
        AuditLogRepository audits = mock(AuditLogRepository.class);
        EmailDeliveryService mail = mock(EmailDeliveryService.class);
        AuthConcurrencyRepository concurrency = mock(AuthConcurrencyRepository.class);
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(0L);
        when(accounts.findByNormalizedEmail("alice@example.com")).thenReturn(java.util.Optional.empty());
        when(accounts.insertIfEmailAvailable(any(), eq("alice@example.com"), eq("PATIENT"),
                eq("PENDING_VERIFICATION"), anyString(), eq("Alice Patient"), any(), any())).thenReturn(1);
        AccountJpaEntity saved = new AccountJpaEntity();
        saved.setId(UUID.randomUUID());
        when(accounts.findById(any())).thenReturn(java.util.Optional.of(saved));
        RegistrationServiceImpl service = new RegistrationServiceImpl(accounts, challenges,
                auditWriter, audits, mail, properties(), concurrency);

        service.register(new fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest(
                "Alice Patient", " Alice@Example.com ", password, password, true), "198.51.100.8");

        verify(accounts).insertIfEmailAvailable(any(), eq("alice@example.com"), eq("PATIENT"),
                eq("PENDING_VERIFICATION"), argThat(hash -> !hash.contains(password)), eq("Alice Patient"), any(), any());
        verify(mail).sendVerificationCode(eq("alice@example.com"), matches("\\d{6}"), eq(saved.getId()));
        // Consent is recorded on the account row and in the audit trail, in the registration transaction.
        assertEquals(ConsentActorType.PATIENT, saved.getConsentActorType());
        assertEquals("v1", saved.getTermsVersion());
        assertEquals("v1", saved.getPrivacyVersion());
        assertNotNull(saved.getConsentAcceptedAt());
        verify(auditWriter).record(argThat(e -> "CONSENT".equals(e.category()) && "ACCEPT_CONSENT".equals(e.action())
                && saved.getId().equals(e.entityId()) && !e.visibleInActivityLog()));
    }

    @Test
    void registrationRejectsPasswordWithoutLetterOrDigitBeforePersistence() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditLogRepository audits = mock(AuditLogRepository.class);
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(0L);
        when(accounts.findByNormalizedEmail(anyString())).thenReturn(java.util.Optional.empty());
        RegistrationServiceImpl service = new RegistrationServiceImpl(accounts,
                mock(VerificationCodeRepository.class), mock(AuditLogWriter.class), audits, mock(EmailDeliveryService.class),
                properties(), mock(AuthConcurrencyRepository.class));

        assertThrows(RegistrationException.class, () -> service.register(
                new fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest(
                        "Alice Patient", "alice@example.com", "12345678", "12345678", true), "198.51.100.8"));
        assertThrows(RegistrationException.class, () -> service.register(
                new fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest(
                        "Alice Patient", "alice@example.com", "abcdefgh", "abcdefgh", true), "198.51.100.9"));
        verify(accounts, never()).insertIfEmailAvailable(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void resendLocksAccountSupersedesCurrentChallengeAndSendsNewOtp() {
        AccountRepository accounts = mock(AccountRepository.class);
        VerificationCodeRepository challenges = mock(VerificationCodeRepository.class);
        EmailDeliveryService mail = mock(EmailDeliveryService.class);
        RegistrationServiceImpl service = new RegistrationServiceImpl(accounts, challenges,
                mock(AuditLogWriter.class), mock(AuditLogRepository.class), mail,
                properties(), mock(AuthConcurrencyRepository.class));
        UUID id = UUID.randomUUID(); AccountJpaEntity account = new AccountJpaEntity(); account.setId(id);
        account.setNormalizedEmail("alice@example.com"); account.setAccountType(AccountType.PATIENT); account.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(accounts.findByNormalizedEmail("alice@example.com")).thenReturn(java.util.Optional.of(account));
        when(accounts.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(account));
        when(challenges.countByAccountIdAndPurposeAndCreatedAtAfter(eq(id), eq(VerificationPurpose.EMAIL_VERIFY), any())).thenReturn(0L);
        service.resendOtp(" Alice@Example.com ");
        verify(accounts).findByIdForUpdate(id);
        // The open code is superseded before the new one is inserted (one open code per account and purpose).
        var order = inOrder(challenges);
        order.verify(challenges).supersedeCurrent(eq(id), eq(VerificationPurpose.EMAIL_VERIFY), any());
        order.verify(challenges).save(argThat(c -> c.getPurpose() == VerificationPurpose.EMAIL_VERIFY && id.equals(c.getAccountId())));
        verify(mail).sendVerificationCode(eq("alice@example.com"), anyString(), eq(id));
    }

    @Test
    void resendEmailFailurePersistsAuditAndPropagatesSafeCode() {
        AccountRepository accounts = mock(AccountRepository.class);
        VerificationCodeRepository challenges = mock(VerificationCodeRepository.class);
        AuditLogWriter audits = mock(AuditLogWriter.class);
        EmailDeliveryService mail = mock(EmailDeliveryService.class);
        RegistrationServiceImpl service = new RegistrationServiceImpl(accounts, challenges, audits,
                mock(AuditLogRepository.class), mail, properties(), mock(AuthConcurrencyRepository.class));
        UUID id = UUID.randomUUID(); AccountJpaEntity account = new AccountJpaEntity(); account.setId(id);
        account.setNormalizedEmail("alice@example.com"); account.setAccountType(AccountType.PATIENT); account.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(accounts.findByNormalizedEmail("alice@example.com")).thenReturn(java.util.Optional.of(account));
        when(accounts.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(account));
        when(challenges.countByAccountIdAndPurposeAndCreatedAtAfter(eq(id), eq(VerificationPurpose.EMAIL_VERIFY), any())).thenReturn(0L);
        doThrow(new RuntimeException("smtp down")).when(mail).sendVerificationCode(eq("alice@example.com"), anyString(), eq(id));
        RegistrationException ex = assertThrows(RegistrationException.class, () -> service.resendOtp("alice@example.com"));
        assertEquals("EMAIL_DELIVERY_UNAVAILABLE", ex.getMessage());
        verify(audits).record(argThat(a -> "RESEND_EMAIL_DELIVERY_UNAVAILABLE".equals(a.outcome())
                && "auth_audits".equals(a.source()) && "EMAIL_VERIFICATION".equals(a.action()) && !a.visibleInActivityLog()));
    }

    private RegistrationServiceImpl service() {
        return new RegistrationServiceImpl(mock(AccountRepository.class),
                mock(VerificationCodeRepository.class), mock(AuditLogWriter.class),
                mock(AuditLogRepository.class), mock(EmailDeliveryService.class), properties(), mock(AuthConcurrencyRepository.class));
    }

    private AuthProperties properties() {
        return new AuthProperties(null, new AuthProperties.RateLimit(5, 3600), new AuthProperties.RateLimit(3, 3600),
                null, null, "v1", "v1", "test-secret");
    }
}
