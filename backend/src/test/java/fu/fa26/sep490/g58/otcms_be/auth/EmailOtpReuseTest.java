package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailOtpReuseTest {
    final EmailOtpService otp = new EmailOtpServiceImpl();
    final AccountRepository accounts = mock(AccountRepository.class);
    final VerificationCodeRepository challenges = mock(VerificationCodeRepository.class);
    final AccountJpaEntity account = new AccountJpaEntity();
    final VerificationCodeJpaEntity challenge = new VerificationCodeJpaEntity();
    final RegistrationServiceImpl registration = new RegistrationServiceImpl(accounts, challenges,
            mock(AuditLogWriter.class), mock(AuditLogRepository.class), mock(EmailDeliveryService.class),
            new AuthProperties(null, null, null, null, null, "v1", "v1"), mock(AuthConcurrencyRepository.class), otp);

    @BeforeEach void setup() {
        account.setId(UUID.randomUUID()); account.setAccountType(AccountType.PATIENT); account.setStatus(AccountStatus.PENDING_VERIFICATION);
        account.setConsentActorType(ConsentActorType.PATIENT); account.setTermsVersion("v1"); account.setPrivacyVersion("v1");
        when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(challenges.findCurrentForUpdate(account.getId(), VerificationPurpose.EMAIL_VERIFY)).thenReturn(Optional.of(challenge));
        challenge.setCodeHash(otp.hash("123456")); challenge.setExpiresAt(Instant.now().plusSeconds(600));
    }
    @Test void reusablePrimitivesKeepUc06FormatHashAndTtl() {
        for (int i = 0; i < 50; i++) assertTrue(otp.generate().matches("[0-9]{6}"));
        String hash = otp.hash("123456"); assertNotEquals("123456", hash);
        assertTrue(hash.startsWith("{bcrypt}")); assertTrue(otp.matches("123456", hash));
        assertFalse(otp.matches("654321", hash)); assertFalse(otp.matches(null, hash));
        Instant created = Instant.parse("2026-11-02T00:00:00Z"); assertEquals(created.plusSeconds(600), otp.expiresAt(created));
    }
    @Test void registrationVerifyStillActivatesAndConsumes() {
        registration.verifyEmail(" PATIENT@EXAMPLE.TEST ", "123456");
        assertEquals(AccountStatus.ACTIVE, account.getStatus()); assertNotNull(challenge.getConsumedAt());
        verify(accounts).save(account); verify(challenges).save(challenge);
    }
    @Test void registrationVerifyWithoutRecordedConsentIsRejected() {
        account.setConsentActorType(null); account.setTermsVersion(null); account.setPrivacyVersion(null);
        assertEquals("CONSENT_REQUIRED", assertThrows(RegistrationException.class,
                () -> registration.verifyEmail("patient@example.test", "123456")).getMessage());
        assertEquals(AccountStatus.PENDING_VERIFICATION, account.getStatus());
    }
    @Test void registrationFifthFailureStillSupersedesWithoutActivating() {
        challenge.setFailedAttempts(4);
        assertEquals("OTP_INVALID_OR_EXPIRED", assertThrows(RegistrationException.class,
                () -> registration.verifyEmail("patient@example.test", "000000")).getMessage());
        assertEquals(5, challenge.getFailedAttempts()); assertNotNull(challenge.getSupersededAt());
        assertEquals(AccountStatus.PENDING_VERIFICATION, account.getStatus());
    }
    @Test void registrationExpiredAndConsumedRemainInvalid() {
        challenge.setExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(RegistrationException.class, () -> registration.verifyEmail("patient@example.test", "123456"));
        assertEquals(0, challenge.getFailedAttempts());
        when(challenges.findCurrentForUpdate(account.getId(), VerificationPurpose.EMAIL_VERIFY)).thenReturn(Optional.empty());
        assertThrows(RegistrationException.class, () -> registration.verifyEmail("patient@example.test", "123456"));
    }
    @ParameterizedTest @ValueSource(longs = {3, 4})
    void registrationResendRateBehaviorUnchanged(long count) {
        when(challenges.countByAccountIdAndPurposeAndCreatedAtAfter(eq(account.getId()), eq(VerificationPurpose.EMAIL_VERIFY), any())).thenReturn(count);
        assertEquals("RATE_LIMIT_EXCEEDED", assertThrows(RegistrationException.class,
                () -> registration.resendOtp("patient@example.test")).getMessage());
        verify(challenges, never()).supersedeCurrent(any(), any(), any());
    }
}
