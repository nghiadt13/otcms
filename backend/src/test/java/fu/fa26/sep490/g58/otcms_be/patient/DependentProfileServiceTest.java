package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.EmailOtpServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.*;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.CareRelationshipMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.patient.repository.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DependentProfileServiceTest {
    final UUID actor = UUID.randomUUID(), own = UUID.randomUUID(), target = UUID.randomUUID(), recipient = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-02T00:00:00Z");
    final LocalDate dob = LocalDate.of(2000, 1, 2);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final PatientIdentityDirectory patients = mock(PatientIdentityDirectory.class);
    final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    final MyAccountQueryService accounts = mock(MyAccountQueryService.class);
    final CareRelationshipRepository links = mock(CareRelationshipRepository.class);
    final CareLinkOtpChallengeRepository challenges = mock(CareLinkOtpChallengeRepository.class);
    final EmailDeliveryService email = mock(EmailDeliveryService.class);
    final EmailOtpService otp = new EmailOtpServiceImpl();
    final DependentProfileServiceImpl service = new DependentProfileServiceImpl(profiles, patients, receptionists,
            accounts, links, challenges, mock(CoordinationGuardService.class), otp, email,
            new AuthProperties(null, null, null, null, null, "v1", "v1"), Clock.fixed(now, ZoneOffset.UTC),
            mock(PlatformTransactionManager.class));

    @BeforeEach void setup() {
        Map<UUID, CareRelationshipJpaEntity> savedLinks = new HashMap<>();
        Map<UUID, VerificationCodeJpaEntity> savedChallenges = new HashMap<>();
        when(links.saveAndFlush(any())).thenAnswer(invocation -> {
            CareRelationshipJpaEntity link = invocation.getArgument(0); savedLinks.put(link.getId(), link); return link;
        });
        when(links.findById(any())).thenAnswer(invocation -> Optional.ofNullable(savedLinks.get(invocation.getArgument(0))));
        when(challenges.saveAndFlush(any())).thenAnswer(invocation -> {
            VerificationCodeJpaEntity c = invocation.getArgument(0); if (c.getId() == null) c.setId(UUID.randomUUID()); savedChallenges.put(c.getId(), c); return c;
        });
        when(challenges.findByIdForUpdate(any())).thenAnswer(invocation -> Optional.ofNullable(savedChallenges.get(invocation.getArgument(0))));
        when(challenges.findCurrentForUpdate(any())).thenAnswer(invocation -> savedChallenges.values().stream()
                .filter(c -> c.getLinkId().equals(invocation.getArgument(0)) && c.getConsumedAt() == null && c.getSupersededAt() == null).findFirst());
        when(challenges.activateIfCurrent(any(), any(), anyString(), any(), anyInt())).thenAnswer(invocation -> {
            var c = savedChallenges.get(invocation.getArgument(0));
            if (c == null || !c.getLinkId().equals(invocation.getArgument(1)) || c.getConsumedAt() != null
                    || c.getSupersededAt() != null || !c.getExpiresAt().isAfter(invocation.getArgument(3))
                    || c.getFailedAttempts() >= (int) invocation.getArgument(4)) return 0;
            c.setCodeHash(invocation.getArgument(2)); return 1;
        });
        when(patients.isActivePatient(actor)).thenReturn(true);
        when(profiles.findPatientProfile(actor)).thenReturn(Optional.of(own));
        when(profiles.findProfile(target)).thenReturn(Optional.of(profile(recipient, "CCCD", "123")));
    }
    ProfileRecord profile(UUID account, String type, String number) {
        return new ProfileRecord(target, account, "Nguyễn An", null, null, dob, "OTHER", null, number, type, 0);
    }
    AddDependentRequest request(String number, String type) {
        return new AddDependentRequest(" Nguyễn   An ", dob, "OTHER", number, type, Relationship.CHILD);
    }
    CareRelationship link(State state, PendingReason reason) {
        return new CareRelationship(UUID.randomUUID(), actor, target, recipient, state, now, null, null, null,
                Relationship.CHILD, null, reason, null, null);
    }
    void existing(ProfileRecord profile) {
        when(profiles.matchDependent(anyString(), eq(dob), nullable(String.class))).thenReturn(List.of(profile));
    }
    void code(String expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(CareRelationshipException.class, action).getMessage());
    }

    @Test void branchACreatesWithoutAccountContactAddressOrEmail() {
        when(profiles.create(any(), isNull())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.add(actor, request("123", "CCCD"));
        assertEquals("LINKED", result.result());
        assertFalse(result.profile().hasAccount());
        assertNull(result.profile().phoneCanonical()); assertNull(result.profile().phoneOwnerType());
        assertNull(result.profile().address());
        var saved = ArgumentCaptor.forClass(CareRelationshipJpaEntity.class);
        verify(links).saveAndFlush(saved.capture());
        assertEquals(State.CONFIRMED, saved.getValue().getState());
        assertEquals(VerificationMethod.CREATED_WITHOUT_OTP, saved.getValue().getVerificationMethod());
        assertEquals(now, saved.getValue().getVerifiedAt()); assertEquals(actor, saved.getValue().getVerifiedByAccountId());
        assertNull(saved.getValue().getConfirmingAccountId());
        verifyNoInteractions(email, challenges, accounts);
    }
    @Test void branchBSendsOnlyToLinkedAccountAndHashesOtp() {
        existing(profile(recipient, "CCCD", "123"));
        when(accounts.find(recipient)).thenReturn(Optional.of(new MyAccountQueryService.MyAccountView(recipient,
                "PATIENT", "dependent@example.test", "Dependent")));
        var result = service.add(actor, request("123", "CCCD"));
        assertEquals("OTP_SENT", result.result()); assertEquals("***@example.test", result.maskedEmail());
        var sent = ArgumentCaptor.forClass(String.class);
        verify(email).sendVerificationCode(eq("dependent@example.test"), sent.capture(), eq(recipient));
        verify(accounts, never()).find(actor);
        var saved = ArgumentCaptor.forClass(VerificationCodeJpaEntity.class);
        verify(challenges).saveAndFlush(saved.capture());
        verify(challenges).activateIfCurrent(eq(saved.getValue().getId()), eq(result.linkId()), anyString(), eq(now), eq(5));
        assertNotEquals(sent.getValue(), saved.getValue().getCodeHash());
        assertTrue(otp.matches(sent.getValue(), saved.getValue().getCodeHash()));
        assertEquals(now.plusSeconds(600), saved.getValue().getExpiresAt());
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"PERSONAL_ID", "PASSPORT"})
    void nonCccdOrLegacyRequiresCounter(String type) {
        existing(profile(recipient, type, "123"));
        when(accounts.find(recipient)).thenReturn(Optional.of(new MyAccountQueryService.MyAccountView(recipient,
                "PATIENT", "dependent@example.test", "Dependent")));
        assertEquals("COUNTER_REQUIRED", service.add(actor, request("123", type == null ? "CCCD" : type)).result());
        verifyNoInteractions(email, challenges);
    }
    @Test void accountlessAndMissingEmailRequireCounter() {
        existing(profile(null, "CCCD", "123"));
        assertEquals("COUNTER_REQUIRED", service.add(actor, request("123", "CCCD")).result());
        existing(profile(recipient, "CCCD", "123"));
        assertEquals("COUNTER_REQUIRED", service.add(actor, request("123", "CCCD")).result());
        verifyNoInteractions(email, challenges);
    }
    @Test void missingDocumentRequiresCounter() {
        existing(profile(recipient, null, null));
        assertEquals("COUNTER_REQUIRED", service.add(actor, request(null, null)).result());
        verifyNoInteractions(email, challenges);
    }
    @Test void ambiguitySelfTypeMismatchAndDuplicateFailClosed() {
        var p = profile(recipient, "CCCD", "123");
        when(profiles.matchDependent(anyString(), eq(dob), eq("123"))).thenReturn(List.of(p, p));
        code("CARE_LINK_NOT_AVAILABLE", () -> service.add(actor, request("123", "CCCD")));
        existing(profile(actor, "CCCD", "123"));
        code("CARE_LINK_NOT_AVAILABLE", () -> service.add(actor, request("123", "CCCD")));
        existing(p);
        code("CARE_LINK_NOT_AVAILABLE", () -> service.add(actor, request("123", "PASSPORT")));
        when(links.findFirstByRequesterAccountIdAndDependentPatientProfileIdAndStateIn(eq(actor), eq(target), any()))
                .thenReturn(Optional.of(CareRelationshipMapper.toEntity(link(State.REQUESTED, PendingReason.COUNTER))));
        code("CARE_LINK_NOT_AVAILABLE", () -> service.add(actor, request("123", "CCCD")));
        verifyNoInteractions(email, challenges);
    }
    @Test void rateLimitAndDeliveryFailureUseSafeCodes() {
        var p = profile(recipient, "CCCD", "123"); existing(p);
        when(accounts.find(recipient)).thenReturn(Optional.of(new MyAccountQueryService.MyAccountView(recipient,
                "PATIENT", "dependent@example.test", "Dependent")));
        when(challenges.countRecipientSince(eq(recipient), any())).thenReturn(3L);
        code("RATE_LIMIT_EXCEEDED", () -> service.add(actor, request("123", "CCCD")));
        when(challenges.countRecipientSince(eq(recipient), any())).thenReturn(0L);
        doThrow(new IllegalStateException("internal details")).when(email).sendVerificationCode(any(), any(), any());
        code("EMAIL_DELIVERY_UNAVAILABLE", () -> service.add(actor, request("123", "CCCD")));
    }
    @ParameterizedTest @ValueSource(strings = {"equal", "expired", "exhausted", "consumed", "superseded"})
    void invalidChallengeConditionsShareSameCode(String condition) {
        var link = link(State.REQUESTED, PendingReason.OTP);
        when(links.findById(link.id())).thenReturn(Optional.of(CareRelationshipMapper.toEntity(link)));
        var challenge = challenge(link.id());
        switch (condition) {
            case "equal" -> challenge.setExpiresAt(now);
            case "expired" -> challenge.setExpiresAt(now.minusSeconds(1));
            case "exhausted" -> challenge.setFailedAttempts(5);
            case "consumed" -> challenge.setConsumedAt(now);
            case "superseded" -> challenge.setSupersededAt(now);
        }
        when(challenges.findCurrentForUpdate(link.id())).thenReturn(Optional.of(challenge));
        code("OTP_INVALID", () -> service.verify(actor, link.id(), "123456"));
        verify(links, never()).saveAndFlush(any());
    }
    VerificationCodeJpaEntity challenge(UUID linkId) {
        var c = new VerificationCodeJpaEntity(); c.setId(UUID.randomUUID()); c.setLinkId(linkId);
        c.setAccountId(recipient); c.setCreatedAt(now); c.setExpiresAt(now.plusSeconds(600));
        c.setCodeHash(otp.hash("123456")); return c;
    }
    @Test void fifthFailureSupersedesAndSavesAttemptEvidence() {
        var link = link(State.REQUESTED, PendingReason.OTP);
        when(links.findById(link.id())).thenReturn(Optional.of(CareRelationshipMapper.toEntity(link)));
        var c = challenge(link.id()); c.setFailedAttempts(4);
        when(challenges.findCurrentForUpdate(link.id())).thenReturn(Optional.of(c));
        code("OTP_INVALID", () -> service.verify(actor, link.id(), "000000"));
        assertEquals(5, c.getFailedAttempts()); assertEquals(now, c.getSupersededAt());
        verify(challenges).saveAndFlush(c);
    }
    @Test void verifyConfirmsWithRequesterAndSameTimestamp() {
        var link = link(State.REQUESTED, PendingReason.OTP);
        when(links.findById(link.id())).thenReturn(Optional.of(CareRelationshipMapper.toEntity(link)));
        var c = challenge(link.id()); when(challenges.findCurrentForUpdate(link.id())).thenReturn(Optional.of(c));
        assertEquals("LINKED", service.verify(actor, link.id(), "123456").result());
        assertEquals(now, c.getConsumedAt());
        var saved = ArgumentCaptor.forClass(CareRelationshipJpaEntity.class); verify(links).saveAndFlush(saved.capture());
        assertEquals(actor, saved.getValue().getVerifiedByAccountId());
        assertEquals(saved.getValue().getConfirmedAt(), saved.getValue().getVerifiedAt());
        assertEquals(VerificationMethod.EMAIL_OTP, saved.getValue().getVerificationMethod());
    }
    @Test void wrongOwnerCheckedBeforeOtpAndInactiveRejected() {
        var l = link(State.REQUESTED, PendingReason.OTP);
        when(links.findById(l.id())).thenReturn(Optional.of(CareRelationshipMapper.toEntity(l)));
        UUID other = UUID.randomUUID(); when(patients.isActivePatient(other)).thenReturn(true);
        when(profiles.findPatientProfile(other)).thenReturn(Optional.of(UUID.randomUUID()));
        code("CARE_LINK_NOT_FOUND", () -> service.verify(other, l.id(), "123456"));
        verifyNoInteractions(challenges);
        when(patients.isActivePatient(actor)).thenReturn(false);
        code("ACCESS_DENIED", () -> service.list(actor));
    }
    @Test void accountWithoutOwnProfileListsNothingInsteadOfFailing() {
        UUID fresh = UUID.randomUUID(); when(patients.isActivePatient(fresh)).thenReturn(true);
        when(profiles.findPatientProfile(fresh)).thenReturn(Optional.empty());
        assertTrue(service.list(fresh).items().isEmpty());
    }
    @Test void sharedValidationRetainsNameAndDocumentRules() {
        var p = AdministrativeProfileFields.record(target, null, " Nguyễn   An ", null, null, dob,
                "other", null, " 123 ", "CCCD", 0, false, true);
        assertEquals("Nguyễn An", p.fullName()); assertEquals("123", p.nationalId());
        assertThrows(PatientProfileException.class, () -> AdministrativeProfileFields.record(target, null,
                "Name", "bad", null, dob, "OTHER", null, "123", "CCCD", 0, false, true));
    }
}
