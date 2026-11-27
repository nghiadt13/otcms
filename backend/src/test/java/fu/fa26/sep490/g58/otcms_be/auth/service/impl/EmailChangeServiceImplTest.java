package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException.Reason;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.EmailChangeAccountWriter;
import fu.fa26.sep490.g58.otcms_be.auth.repository.VerificationCodeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailOtpService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailChangeServiceImplTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final VerificationCodeRepository challenges = mock(VerificationCodeRepository.class);
    private final EmailOtpService otp = mock(EmailOtpService.class);
    private final EmailDeliveryService delivery = mock(EmailDeliveryService.class);
    private final AuthConcurrencyRepository coordination = mock(AuthConcurrencyRepository.class);
    private final EmailChangeAccountWriter emailWriter = mock(EmailChangeAccountWriter.class);
    private final AuthProperties properties = new AuthProperties(null, null,
            new AuthProperties.RateLimit(3, 3600), null, null, null, null);
    private final EmailChangeServiceImpl service = new EmailChangeServiceImpl(accounts, challenges, otp, delivery,
            properties, coordination, emailWriter);
    private final UUID accountId = UUID.randomUUID();
    private final AuthenticatedAccount patient = new AuthenticatedAccount(accountId, AccountRole.PATIENT, UUID.randomUUID());
    private final Instant future = Instant.now().plusSeconds(600);

    private AccountJpaEntity activePatient() {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setId(accountId);
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setNormalizedEmail("old@example.test");
        return account;
    }

    private VerificationCodeJpaEntity pendingChallenge(String targetEmail, String codeHash, Instant expiresAt,
            int failedAttempts) {
        VerificationCodeJpaEntity challenge = new VerificationCodeJpaEntity();
        challenge.setAccountId(accountId);
        challenge.setPurpose(VerificationPurpose.EMAIL_CHANGE);
        challenge.setTargetEmail(targetEmail);
        challenge.setCodeHash(codeHash);
        challenge.setExpiresAt(expiresAt);
        challenge.setFailedAttempts(failedAttempts);
        return challenge;
    }

    @Test
    void requestRejectsMalformedEmailBeforeSupersedingOrSending() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));

        for (String bad : new String[]{"not-an-email", null, "a".repeat(250) + "@x.co", "new\u0001@example.test"}) {
            MyAccountOperationException ex = assertThrows(MyAccountOperationException.class,
                    () -> service.request(patient, bad));
            assertEquals(Reason.INVALID_INPUT, ex.reason());
        }
        verify(challenges, never()).supersedeCurrent(any(), any(), any());
        verifyNoInteractions(delivery);
    }

    @Test
    void requestRejectsNonPatientActorBeforeAnyAccountLookup() {
        AuthenticatedAccount receptionist = new AuthenticatedAccount(accountId, AccountRole.RECEPTIONIST, UUID.randomUUID());
        AuthenticatedAccount noId = new AuthenticatedAccount(null, AccountRole.PATIENT, UUID.randomUUID());

        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.request(receptionist, "new@example.test")).reason());
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.request(null, "new@example.test")).reason());
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.request(noId, "new@example.test")).reason());
        verifyNoInteractions(accounts, challenges, delivery);
    }

    @Test
    void requestSupersedesOldCodeButIssuesNothingWhenEmailMatchesCurrent() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));

        service.request(patient, " Old@Example.test ");

        verify(challenges).supersedeCurrent(eq(accountId), eq(VerificationPurpose.EMAIL_CHANGE), any(Instant.class));
        verify(accounts, never()).existsByNormalizedEmail(any());
        verify(challenges, never()).saveAndFlush(any(VerificationCodeJpaEntity.class));
        verifyNoInteractions(delivery);
    }

    @Test
    void requestSupersedesOldCodeButIssuesNothingWhenEmailIsAlreadyRegistered() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(accounts.existsByNormalizedEmail("taken@example.test")).thenReturn(true);

        service.request(patient, " Taken@Example.test ");

        verify(challenges).supersedeCurrent(eq(accountId), eq(VerificationPurpose.EMAIL_CHANGE), any(Instant.class));
        verify(challenges, never()).saveAndFlush(any(VerificationCodeJpaEntity.class));
        verifyNoInteractions(delivery);
    }

    @Test
    void requestIssuesNothingOnceResendWindowLimitIsReached() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(challenges.countByAccountIdAndPurposeAndCreatedAtAfter(eq(accountId),
                eq(VerificationPurpose.EMAIL_CHANGE), any(Instant.class))).thenReturn(3L);

        service.request(patient, "new@example.test");

        verify(otp, never()).generate();
        verify(challenges, never()).saveAndFlush(any(VerificationCodeJpaEntity.class));
        verifyNoInteractions(delivery);
    }

    @Test
    void requestStoresHashedChallengeAndDeliversCodeToNormalizedAddress() {
        Instant expires = Instant.parse("2026-11-20T01:15:00Z");
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(otp.generate()).thenReturn("123456");
        when(otp.hash("123456")).thenReturn("hash-123");
        when(otp.expiresAt(any(Instant.class))).thenReturn(expires);
        when(challenges.saveAndFlush(any(VerificationCodeJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        service.request(patient, " New@Example.test ");

        ArgumentCaptor<VerificationCodeJpaEntity> saved = ArgumentCaptor.forClass(VerificationCodeJpaEntity.class);
        verify(challenges).saveAndFlush(saved.capture());
        VerificationCodeJpaEntity challenge = saved.getValue();
        assertEquals(accountId, challenge.getAccountId());
        assertEquals(VerificationPurpose.EMAIL_CHANGE, challenge.getPurpose());
        assertEquals("new@example.test", challenge.getTargetEmail());
        assertEquals("hash-123", challenge.getCodeHash());
        assertEquals(expires, challenge.getExpiresAt());
        assertNull(challenge.getSupersededAt());
        verify(delivery).sendVerificationCode("new@example.test", "123456", accountId);
    }

    @Test
    void requestMarksChallengeSupersededWhenDeliveryFailsWithoutFailingTheRequest() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(otp.generate()).thenReturn("654321");
        when(otp.hash("654321")).thenReturn("hash-654");
        when(challenges.saveAndFlush(any(VerificationCodeJpaEntity.class))).thenAnswer(call -> call.getArgument(0));
        doThrow(new IllegalStateException("mail relay unavailable"))
                .when(delivery).sendVerificationCode("new@example.test", "654321", accountId);

        service.request(patient, "new@example.test");

        ArgumentCaptor<VerificationCodeJpaEntity> saved = ArgumentCaptor.forClass(VerificationCodeJpaEntity.class);
        verify(challenges).saveAndFlush(saved.capture());
        assertNotNull(saved.getValue().getSupersededAt());
    }

    @Test
    void verifyRejectsWhenNoCurrentChallengeExists() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(challenges.findCurrentForUpdate(accountId, VerificationPurpose.EMAIL_CHANGE)).thenReturn(Optional.empty());

        MyAccountOperationException ex = assertThrows(MyAccountOperationException.class,
                () -> service.verify(patient, "123456"));

        assertEquals(Reason.OTP_INVALID, ex.reason());
        verifyNoInteractions(otp, coordination, emailWriter);
    }

    @Test
    void verifyRejectsExpiredOrExhaustedChallengeWithoutCheckingTheCode() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(challenges.findCurrentForUpdate(accountId, VerificationPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(
                pendingChallenge("new@example.test", "hash-123", Instant.now().minusSeconds(60), 0)));
        assertEquals(Reason.OTP_INVALID,
                assertThrows(MyAccountOperationException.class, () -> service.verify(patient, "123456")).reason());

        when(challenges.findCurrentForUpdate(accountId, VerificationPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(
                pendingChallenge("new@example.test", "hash-123", future, EmailOtpService.MAX_FAILED_ATTEMPTS)));
        assertEquals(Reason.OTP_INVALID,
                assertThrows(MyAccountOperationException.class, () -> service.verify(patient, "123456")).reason());

        verify(otp, never()).matches(any(), any());
    }

    @Test
    void malformedCodeCountsAsFailedAttemptWithoutComparingHash() {
        VerificationCodeJpaEntity challenge = pendingChallenge("new@example.test", "hash-123", future, 1);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(challenges.findCurrentForUpdate(accountId, VerificationPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(challenge));

        assertEquals(Reason.OTP_INVALID,
                assertThrows(MyAccountOperationException.class, () -> service.verify(patient, "12ab56")).reason());
        assertEquals(Reason.OTP_INVALID,
                assertThrows(MyAccountOperationException.class, () -> service.verify(patient, null)).reason());

        assertEquals(3, challenge.getFailedAttempts());
        assertNull(challenge.getSupersededAt());
        verify(otp, never()).matches(any(), any());
    }

    @Test
    void wrongCodeAtAttemptLimitSupersedesChallenge() {
        VerificationCodeJpaEntity challenge = pendingChallenge("new@example.test", "hash-123", future,
                EmailOtpService.MAX_FAILED_ATTEMPTS - 1);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(challenges.findCurrentForUpdate(accountId, VerificationPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(challenge));
        when(otp.matches("000000", "hash-123")).thenReturn(false);

        assertEquals(Reason.OTP_INVALID,
                assertThrows(MyAccountOperationException.class, () -> service.verify(patient, "000000")).reason());

        assertEquals(EmailOtpService.MAX_FAILED_ATTEMPTS, challenge.getFailedAttempts());
        assertNotNull(challenge.getSupersededAt());
        verify(emailWriter, never()).updateVerifiedEmail(any(), any(), any());
    }

    @Test
    void verifyRejectsEmailClaimedMeanwhileAndSupersedesChallenge() {
        VerificationCodeJpaEntity challenge = pendingChallenge("taken@example.test", "hash-123", future, 0);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(activePatient()));
        when(challenges.findCurrentForUpdate(accountId, VerificationPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(challenge));
        when(otp.matches("123456", "hash-123")).thenReturn(true);
        when(accounts.existsByNormalizedEmail("taken@example.test")).thenReturn(true);

        assertEquals(Reason.EMAIL_UNAVAILABLE,
                assertThrows(MyAccountOperationException.class, () -> service.verify(patient, "123456")).reason());

        verify(coordination).lock("registration-email:taken@example.test");
        assertNotNull(challenge.getSupersededAt());
        assertNull(challenge.getConsumedAt());
        verify(emailWriter, never()).updateVerifiedEmail(any(), any(), any());
    }

    @Test
    void verifyWritesVerifiedEmailThroughWriterAndConsumesChallenge() {
        AccountJpaEntity account = activePatient();
        VerificationCodeJpaEntity challenge = pendingChallenge("new@example.test", "hash-123", future, 0);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(challenges.findCurrentForUpdate(accountId, VerificationPurpose.EMAIL_CHANGE)).thenReturn(Optional.of(challenge));
        when(otp.matches("123456", "hash-123")).thenReturn(true);
        when(accounts.existsByNormalizedEmail("new@example.test")).thenReturn(false);

        service.verify(patient, "123456");

        verify(coordination).lock("registration-email:new@example.test");
        verify(emailWriter).updateVerifiedEmail(eq(account), eq("new@example.test"), any(Instant.class));
        assertNotNull(challenge.getConsumedAt());
        verify(challenges).flush();
    }

    @Test
    void requestRejectsAccountThatIsNotAnActivePatientWithoutIssuingCode() {
        AccountJpaEntity staffType = activePatient();
        staffType.setAccountType(AccountType.RECEPTIONIST);
        AccountJpaEntity disabled = activePatient();
        disabled.setStatus(AccountStatus.DISABLED);
        AccountJpaEntity mustChange = activePatient();
        mustChange.setMustChangePassword(true);

        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(staffType));
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.request(patient, "new@example.test")).reason());
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(disabled));
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.request(patient, "new@example.test")).reason());
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(mustChange));
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.request(patient, "new@example.test")).reason());
        verifyNoInteractions(challenges, delivery);
    }
}
