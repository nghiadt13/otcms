package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailOtpService;
import fu.fa26.sep490.g58.otcms_be.auth.service.MyAccountQueryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.MyAccountQueryService.MyAccountView;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareOtpInvalidException;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareRelationshipException;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.Relationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.DependentAddPayload;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.AddDependentRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.LinkResult;
import fu.fa26.sep490.g58.otcms_be.patient.repository.DependentAddRequestRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependentAddOtpServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-00000000d002");

    @Mock private PatientIdentityDirectory patients;
    @Mock private MyAccountQueryService accounts;
    @Mock private DependentAddRequestRepository requests;
    @Mock private CoordinationGuardService guard;
    @Mock private EmailOtpService otp;
    @Mock private EmailDeliveryService email;
    @Mock private DependentProfileService dependents;
    @Mock private PlatformTransactionManager txManager;

    private DependentAddOtpServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        AuthProperties auth = new AuthProperties(null, null, new AuthProperties.RateLimit(3, 3600), null, null, "v1", "v1");
        service = new DependentAddOtpServiceImpl(patients, accounts, requests, guard, otp, email, auth,
                Clock.fixed(NOW, ZoneOffset.UTC), dependents, txManager);
    }

    private static AddDependentRequest validRequest() {
        return new AddDependentRequest("Tran Minh", LocalDate.of(2015, 5, 10), "MALE",
                "012345678901", "CCCD", Relationship.CHILD);
    }

    private static MyAccountView patientView() {
        return new MyAccountView(ACTOR, "PATIENT", "patient@example.com", "Nguyen An");
    }

    private static VerificationCodeJpaEntity storedChallenge(Instant expiresAt, int failedAttempts) {
        VerificationCodeJpaEntity row = new VerificationCodeJpaEntity();
        row.setId(REQUEST_ID);
        row.setAccountId(ACTOR);
        row.setPurpose(VerificationPurpose.DEPENDENT_ADD);
        row.setCodeHash("code-hash");
        row.setExpiresAt(expiresAt);
        row.setFailedAttempts(failedAttempts);
        row.setPayload(new DependentAddPayload("Tran Minh", LocalDate.of(2015, 5, 10), "MALE",
                "012345678901", "CCCD", "CHILD"));
        return row;
    }

    @Test
    void requestValidDependentStoresChallengeSendsOtpAndActivates() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);
        when(accounts.find(ACTOR)).thenReturn(Optional.of(patientView()));
        when(otp.generate()).thenReturn("123456");
        when(otp.hash("DELIVERY_PENDING")).thenReturn("pending-hash");
        when(otp.hash("123456")).thenReturn("code-hash");
        when(otp.expiresAt(NOW)).thenReturn(NOW.plusSeconds(600));
        when(requests.countRequestsSince(eq(ACTOR), any())).thenReturn(0L);
        when(requests.saveAndFlush(any(VerificationCodeJpaEntity.class))).thenAnswer(call -> {
            VerificationCodeJpaEntity row = call.getArgument(0);
            row.setId(REQUEST_ID);
            return row;
        });
        when(requests.activateIfCurrent(REQUEST_ID, "code-hash", NOW, EmailOtpService.MAX_FAILED_ATTEMPTS)).thenReturn(1);

        var result = service.request(ACTOR, validRequest());

        assertEquals(REQUEST_ID, result.requestId());
        assertNotNull(result.maskedEmail());
        verify(email).sendVerificationCode("patient@example.com", "123456", ACTOR);
        verify(requests).activateIfCurrent(REQUEST_ID, "code-hash", NOW, EmailOtpService.MAX_FAILED_ATTEMPTS);
    }

    @Test
    void verifyValidOtpConsumesChallengeAndDelegatesToDependentProfileService() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);
        VerificationCodeJpaEntity row = storedChallenge(NOW.plusSeconds(300), 0);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(row));
        when(otp.matches("123456", "code-hash")).thenReturn(true);
        LinkResult expected = mock(LinkResult.class);
        when(dependents.add(eq(ACTOR), any(AddDependentRequest.class))).thenReturn(expected);

        var result = service.verify(ACTOR, REQUEST_ID, "123456");

        assertSame(expected, result);
        assertEquals(NOW, row.getConsumedAt());
        assertNull(row.getPayload());
        verify(dependents).add(eq(ACTOR), any(AddDependentRequest.class));
    }

    @Test
    void requestRejectsInactiveOrNullActorBeforeValidation() {
        when(patients.isActivePatient(ACTOR)).thenReturn(false);

        assertEquals("ACCESS_DENIED", assertThrows(CareRelationshipException.class,
                () -> service.request(null, validRequest())).getMessage());
        assertEquals("ACCESS_DENIED", assertThrows(CareRelationshipException.class,
                () -> service.request(ACTOR, validRequest())).getMessage());
        verifyNoInteractions(accounts, requests, email);
    }

    @Test
    void requestRejectsNullRequestOrMissingRelationshipBeforeEmailLookup() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);
        var noRelationship = new AddDependentRequest("Tran Minh", LocalDate.of(2015, 5, 10), "MALE",
                "012345678901", "CCCD", null);

        assertEquals("VALIDATION_FAILED", assertThrows(CareRelationshipException.class,
                () -> service.request(ACTOR, null)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(CareRelationshipException.class,
                () -> service.request(ACTOR, noRelationship)).getMessage());
        verifyNoInteractions(accounts, requests, email);
    }

    @Test
    void requestRejectsMissingEmailAndRateLimitExceeded() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);
        when(accounts.find(ACTOR)).thenReturn(Optional.of(new MyAccountView(ACTOR, "PATIENT", "invalid-email", "Nguyen An")));

        assertEquals("EMAIL_DELIVERY_UNAVAILABLE", assertThrows(CareRelationshipException.class,
                () -> service.request(ACTOR, validRequest())).getMessage());
        verifyNoInteractions(requests, email);

        when(accounts.find(ACTOR)).thenReturn(Optional.of(patientView()));
        when(otp.generate()).thenReturn("123456");
        when(requests.countRequestsSince(eq(ACTOR), any())).thenReturn(3L);

        assertEquals("RATE_LIMIT_EXCEEDED", assertThrows(CareRelationshipException.class,
                () -> service.request(ACTOR, validRequest())).getMessage());
        verifyNoInteractions(email);
    }

    @Test
    void requestSupersedesChallengeWhenEmailDeliveryFails() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);
        when(accounts.find(ACTOR)).thenReturn(Optional.of(patientView()));
        when(otp.generate()).thenReturn("123456");
        when(otp.hash("DELIVERY_PENDING")).thenReturn("pending-hash");
        when(otp.expiresAt(NOW)).thenReturn(NOW.plusSeconds(600));
        when(requests.countRequestsSince(eq(ACTOR), any())).thenReturn(0L);
        VerificationCodeJpaEntity saved = storedChallenge(NOW.plusSeconds(600), 0);
        when(requests.saveAndFlush(any(VerificationCodeJpaEntity.class))).thenReturn(saved);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(saved));
        doThrow(new RuntimeException("smtp error")).when(email).sendVerificationCode(anyString(), anyString(), any());

        assertEquals("EMAIL_DELIVERY_UNAVAILABLE", assertThrows(CareRelationshipException.class,
                () -> service.request(ACTOR, validRequest())).getMessage());
        assertEquals(NOW, saved.getSupersededAt());
        assertNull(saved.getPayload());
    }

    @Test
    void resendGuardsNullUnknownForeignConsumedAndIssuesNewOtpWhenValid() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);

        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.resend(ACTOR, null)).getMessage());
        verifyNoInteractions(requests);

        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.empty());
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.resend(ACTOR, REQUEST_ID)).getMessage());

        VerificationCodeJpaEntity foreignOrNullOwner = storedChallenge(NOW.plusSeconds(300), 0);
        foreignOrNullOwner.setAccountId(null);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(foreignOrNullOwner));
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.resend(ACTOR, REQUEST_ID)).getMessage());

        VerificationCodeJpaEntity consumed = storedChallenge(NOW.plusSeconds(300), 0);
        consumed.setConsumedAt(NOW);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(consumed));
        assertEquals("CARE_LINK_NOT_AVAILABLE", assertThrows(CareRelationshipException.class,
                () -> service.resend(ACTOR, REQUEST_ID)).getMessage());

        VerificationCodeJpaEntity valid = storedChallenge(NOW.plusSeconds(300), 0);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(valid));
        when(accounts.find(ACTOR)).thenReturn(Optional.of(patientView()));
        when(otp.generate()).thenReturn("654321");
        when(otp.hash("DELIVERY_PENDING")).thenReturn("pending-hash");
        when(otp.hash("654321")).thenReturn("new-hash");
        when(otp.expiresAt(NOW)).thenReturn(NOW.plusSeconds(600));
        when(requests.countRequestsSince(eq(ACTOR), any())).thenReturn(0L);
        when(requests.saveAndFlush(any(VerificationCodeJpaEntity.class))).thenAnswer(call -> {
            VerificationCodeJpaEntity row = call.getArgument(0);
            row.setId(REQUEST_ID);
            return row;
        });
        when(requests.activateIfCurrent(REQUEST_ID, "new-hash", NOW, EmailOtpService.MAX_FAILED_ATTEMPTS)).thenReturn(1);

        var result = service.resend(ACTOR, REQUEST_ID);
        assertEquals(REQUEST_ID, result.requestId());
        assertEquals("***@example.com", result.maskedEmail());
    }

    @Test
    void verifyRejectsExpiredOrUnknownChallengeWithoutCallingDependentService() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);
        VerificationCodeJpaEntity expired = storedChallenge(NOW.minusSeconds(1), 0);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(expired));

        assertThrows(CareOtpInvalidException.class, () -> service.verify(ACTOR, REQUEST_ID, "123456"));
        verifyNoInteractions(dependents);
    }

    @Test
    void verifyGuardsNullIdNullOwnerNullExpiryNullPayloadAndMalformedCode() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);

        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.verify(ACTOR, null, "123456")).getMessage());
        verifyNoInteractions(requests);

        VerificationCodeJpaEntity nullOwner = storedChallenge(NOW.plusSeconds(300), 0);
        nullOwner.setAccountId(null);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(nullOwner));
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.verify(ACTOR, REQUEST_ID, "123456")).getMessage());

        VerificationCodeJpaEntity nullExpiry = storedChallenge(null, 0);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(nullExpiry));
        assertThrows(CareOtpInvalidException.class, () -> service.verify(ACTOR, REQUEST_ID, "123456"));

        VerificationCodeJpaEntity nullPayload = storedChallenge(NOW.plusSeconds(300), 0);
        nullPayload.setPayload(null);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(nullPayload));
        assertThrows(CareOtpInvalidException.class, () -> service.verify(ACTOR, REQUEST_ID, "123456"));

        VerificationCodeJpaEntity malformed = storedChallenge(NOW.plusSeconds(300), 0);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(malformed));
        assertThrows(CareOtpInvalidException.class, () -> service.verify(ACTOR, REQUEST_ID, "12ab56"));
        assertEquals(1, malformed.getFailedAttempts());
        verify(otp, never()).matches(any(), any());
        verifyNoInteractions(dependents);
    }

    @Test
    void verifyWrongCodeAtMaxAttemptsSupersedesChallengeAndThrowsOtpInvalid() {
        when(patients.isActivePatient(ACTOR)).thenReturn(true);
        VerificationCodeJpaEntity row = storedChallenge(NOW.plusSeconds(300), EmailOtpService.MAX_FAILED_ATTEMPTS - 1);
        when(requests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(row));
        when(otp.matches("000000", "code-hash")).thenReturn(false);

        assertThrows(CareOtpInvalidException.class, () -> service.verify(ACTOR, REQUEST_ID, "000000"));
        assertEquals(EmailOtpService.MAX_FAILED_ATTEMPTS, row.getFailedAttempts());
        assertEquals(NOW, row.getSupersededAt());
        verifyNoInteractions(dependents);
    }
}
