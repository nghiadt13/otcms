package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.RegistrationResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.ConsentActorType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.VerificationCodeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailOtpService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String EMAIL = "new.patient@example.com";
    private static final String GENERIC = "If registration can be completed, a verification code will be sent.";

    @Mock private AccountRepository accounts;
    @Mock private VerificationCodeRepository challenges;
    @Mock private AuditLogWriter auditWriter;
    @Mock private AuditLogRepository auditLogs;
    @Mock private EmailDeliveryService emailDelivery;
    @Mock private AuthConcurrencyRepository concurrency;
    @Mock private EmailOtpService otp;
    @Mock private AuthProperties properties;

    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.register()).thenReturn(new AuthProperties.RateLimit(5, 3600));
        lenient().when(properties.resend()).thenReturn(new AuthProperties.RateLimit(3, 3600));
        lenient().when(properties.termsVersion()).thenReturn("v1");
        lenient().when(properties.privacyVersion()).thenReturn("v1");
        lenient().when(properties.rateLimitHmacSecret()).thenReturn("test-hmac-secret");
        service = new RegistrationServiceImpl(accounts, challenges, auditWriter, auditLogs, emailDelivery,
                properties, concurrency, otp);
    }

    private static RegistrationRequest request(String fullName, String password, String confirm, boolean consent) {
        return new RegistrationRequest(fullName, EMAIL, password, confirm, consent);
    }

    private static RegistrationRequest validRequest() {
        return request("Jane Doe", "Passw0rd1", "Passw0rd1", true);
    }

    private static AccountJpaEntity account(AccountType type, AccountStatus status) {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setId(ACCOUNT_ID);
        account.setNormalizedEmail(EMAIL);
        account.setAccountType(type);
        account.setStatus(status);
        return account;
    }

    @Test
    void registerNewPatientCreatesPendingAccountAndSendsOtp() {
        when(accounts.findByNormalizedEmail(EMAIL)).thenReturn(Optional.empty());
        when(accounts.insertIfEmailAvailable(any(), eq(EMAIL), eq("PATIENT"), eq("PENDING_VERIFICATION"),
                anyString(), eq("Jane Doe"), any(), any())).thenReturn(1);
        AccountJpaEntity saved = account(AccountType.PATIENT, AccountStatus.PENDING_VERIFICATION);
        when(accounts.findById(any())).thenReturn(Optional.of(saved));
        when(otp.generate()).thenReturn("123456");
        when(otp.hash("123456")).thenReturn("code-hash");
        when(otp.expiresAt(any(Instant.class))).thenReturn(Instant.now().plusSeconds(600));

        RegistrationResponse response = service.register(validRequest(), "203.0.113.5");

        assertEquals(GENERIC, response.message());
        assertEquals(ConsentActorType.PATIENT, saved.getConsentActorType());
        verify(emailDelivery).sendVerificationCode(EMAIL, "123456", ACCOUNT_ID);
        verify(auditWriter).record(argThat(e -> "CONSENT".equals(e.category()) && "ACCEPT_CONSENT".equals(e.action())));
    }

    @Test
    void registerPendingPatientResendsOtpWithinQuota() {
        when(accounts.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(account(AccountType.PATIENT, AccountStatus.PENDING_VERIFICATION)));
        when(challenges.countByAccountIdAndPurposeAndCreatedAtAfter(eq(ACCOUNT_ID), eq(VerificationPurpose.EMAIL_VERIFY), any())).thenReturn(0L);
        when(otp.generate()).thenReturn("123456");
        when(otp.hash("123456")).thenReturn("code-hash");
        when(otp.expiresAt(any(Instant.class))).thenReturn(Instant.now().plusSeconds(600));

        RegistrationResponse response = service.register(validRequest(), "203.0.113.5");

        assertEquals(GENERIC, response.message());
        verify(emailDelivery).sendVerificationCode(EMAIL, "123456", ACCOUNT_ID);
    }

    @Test
    void registerRejectsMismatchedPasswordOrMissingConsentBeforeLookup() {
        RegistrationException mismatch = assertThrows(RegistrationException.class,
                () -> service.register(request("Jane Doe", "Passw0rd1", "Passw0rd2", true), "203.0.113.5"));
        RegistrationException noConsent = assertThrows(RegistrationException.class,
                () -> service.register(request("Jane Doe", "Passw0rd1", "Passw0rd1", false), "203.0.113.5"));

        assertEquals("VALIDATION_FAILED", mismatch.getMessage());
        assertEquals("VALIDATION_FAILED", noConsent.getMessage());
        verify(accounts, never()).findByNormalizedEmail(any());
    }

    @Test
    void registerRejectsPasswordWithoutLetterOrDigitBeforePersistence() {
        RegistrationException digitsOnly = assertThrows(RegistrationException.class,
                () -> service.register(request("Jane Doe", "12345678", "12345678", true), "203.0.113.5"));

        assertEquals("VALIDATION_FAILED", digitsOnly.getMessage());
        verify(accounts, never()).insertIfEmailAvailable(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerIgnoresAlreadyActivePatientWithoutSendingOtp() {
        when(accounts.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(account(AccountType.PATIENT, AccountStatus.ACTIVE)));

        RegistrationResponse response = service.register(validRequest(), "203.0.113.5");

        assertEquals(GENERIC, response.message());
        verifyNoInteractions(emailDelivery);
    }

    @Test
    void registerThrowsWhenRateLimitExceeded() {
        when(auditLogs.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(
                anyString(), anyString(), anyString(), anyString(), any())).thenReturn(5L);

        RegistrationException ex = assertThrows(RegistrationException.class,
                () -> service.register(validRequest(), "203.0.113.5"));

        assertEquals("RATE_LIMIT_EXCEEDED", ex.getMessage());
        verify(accounts, never()).findByNormalizedEmail(any());
    }

    @Test
    void registerRejectsFullNameLongerThanOneHundredCharacters() {
        RegistrationException ex = assertThrows(RegistrationException.class,
                () -> service.register(request("n".repeat(101), "Passw0rd1", "Passw0rd1", true), "203.0.113.5"));

        assertEquals("VALIDATION_FAILED", ex.getMessage());
        verify(accounts, never()).findByNormalizedEmail(any());
    }

    @Test
    void registerRejectsNullRequestOrMissingRequiredFieldsBeforeLookup() {
        assertEquals("VALIDATION_FAILED", assertThrows(RegistrationException.class,
                () -> service.register(null, "203.0.113.5")).getMessage());
        for (RegistrationRequest invalid : new RegistrationRequest[]{
                new RegistrationRequest(null, EMAIL, "Passw0rd1", "Passw0rd1", true),
                new RegistrationRequest("Jane Doe", null, "Passw0rd1", "Passw0rd1", true),
                new RegistrationRequest("Jane Doe", "   ", "Passw0rd1", "Passw0rd1", true),
                new RegistrationRequest("Jane Doe", EMAIL, null, "Passw0rd1", true),
                new RegistrationRequest("Jane Doe", EMAIL, "Passw0rd1", null, true)
        }) {
            assertEquals("VALIDATION_FAILED", assertThrows(RegistrationException.class,
                    () -> service.register(invalid, "203.0.113.5")).getMessage());
        }
        verify(accounts, never()).findByNormalizedEmail(any());
    }
}
