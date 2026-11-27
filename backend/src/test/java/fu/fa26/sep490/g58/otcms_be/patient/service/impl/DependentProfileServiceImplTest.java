package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailOtpService;
import fu.fa26.sep490.g58.otcms_be.auth.service.MyAccountQueryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.MyAccountQueryService.MyAccountView;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareOtpInvalidException;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareRelationshipException;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.CareRelationshipMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.PendingReason;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.Relationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.VerificationMethod;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.AddDependentRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.ProfileItem;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.UpdateDependentRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.entity.CareRelationshipJpaEntity;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareLinkOtpChallengeRepository;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class DependentProfileServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-20T02:00:00Z");
    private static final String PARENT_EMAIL = "parent@example.test";

    private final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    private final PatientIdentityDirectory patients = mock(PatientIdentityDirectory.class);
    private final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    private final MyAccountQueryService accounts = mock(MyAccountQueryService.class);
    private final CareRelationshipRepository links = mock(CareRelationshipRepository.class);
    private final CareLinkOtpChallengeRepository challenges = mock(CareLinkOtpChallengeRepository.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final EmailOtpService otp = mock(EmailOtpService.class);
    private final EmailDeliveryService email = mock(EmailDeliveryService.class);
    private final AuthProperties auth = mock(AuthProperties.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final DependentProfileServiceImpl service = new DependentProfileServiceImpl(profiles, patients, receptionists,
            accounts, links, challenges, guard, otp, email, auth, Clock.fixed(NOW, ZoneOffset.UTC), transactions);

    private final UUID actor = UUID.randomUUID();
    private final UUID ownProfileId = UUID.randomUUID();
    private final UUID depProfileId = UUID.randomUUID();
    private final UUID depAccountId = UUID.randomUUID();
    private final UUID linkId = UUID.randomUUID();

    @Test
    void addRejectsMissingRequestWithValidationFailed() {
        allowPatient();
        assertEquals("VALIDATION_FAILED", assertThrows(CareRelationshipException.class,
                () -> service.add(actor, null)).getMessage());
    }

    @Test
    void addCreatesDependentProfileAndConfirmsLinkWhenNoProfileMatches() {
        allowPatient();
        when(profiles.create(any(), isNull())).thenReturn(profile(depProfileId, null, null, null));
        var result = service.add(actor, addRequest(null, null));
        assertEquals("LINKED", result.result());
        assertNotNull(result.linkId());
        assertNotNull(result.profile());
        verify(links).saveAndFlush(any());
    }

    @Test
    void addRejectsCandidateThatIsTheActorsOwnPatientProfile() {
        allowPatient();
        stubCandidate(profile(ownProfileId, depAccountId, null, null));
        assertEquals("CARE_LINK_NOT_AVAILABLE", assertThrows(CareRelationshipException.class,
                () -> service.add(actor, addRequest(null, null))).getMessage());
    }

    @Test
    void addRejectsWhenActiveOrRequestedLinkAlreadyExistsForCandidate() {
        allowPatient();
        stubCandidate(profile(depProfileId, depAccountId, null, null));
        when(links.findFirstByRequesterAccountIdAndDependentPatientProfileIdAndStateIn(eq(actor), eq(depProfileId), any()))
                .thenReturn(Optional.of(CareRelationshipMapper.toEntity(pendingOtp(linkId))));
        assertEquals("CARE_LINK_NOT_AVAILABLE", assertThrows(CareRelationshipException.class,
                () -> service.add(actor, addRequest(null, null))).getMessage());
    }

    @Test
    void addRoutesToCounterWhenCandidateIsNotCccd() {
        allowPatient();
        stubCandidate(profile(depProfileId, depAccountId, "PASSPORT", "A1234567"));
        stubRecipient(PARENT_EMAIL);
        var result = service.add(actor, addRequest("PASSPORT", "A1234567"));
        assertEquals("COUNTER_REQUIRED", result.result());
        assertNull(result.maskedEmail());
        verify(email, never()).sendVerificationCode(any(), any(), any());
    }

    @Test
    void addSendsOtpAndKeepsLinkRequestedWhenCccdCandidateHasRecipientEmail() {
        allowPatient();
        stubCandidate(profile(depProfileId, depAccountId, "CCCD", "012345678901"));
        stubRecipient(PARENT_EMAIL);
        stubOtpDelivery();
        var result = service.add(actor, addRequest("CCCD", "012345678901"));
        assertEquals("OTP_SENT", result.result());
        assertEquals("***@example.test", result.maskedEmail());
        verify(email).sendVerificationCode(PARENT_EMAIL, "123456", depAccountId);
        verify(challenges).activateIfCurrent(any(), any(), eq("hash-123456"), any(), anyInt());
    }

    @Test
    void verifyRejectsChallengeIssuedToAnotherAccountWithOtpInvalid() {
        allowPatient();
        stubPendingLinkOwnedBy(depAccountId);
        when(challenges.findCurrentForUpdate(linkId)).thenReturn(Optional.of(challenge(UUID.randomUUID())));
        assertThrows(CareOtpInvalidException.class, () -> service.verify(actor, linkId, "123456"));
    }

    @Test
    void verifyRejectsChallengeWithNullExpiryWithOtpInvalid() {
        allowPatient();
        stubPendingLinkOwnedBy(depAccountId);
        var current = challenge(depAccountId);
        current.setExpiresAt(null);
        when(challenges.findCurrentForUpdate(linkId)).thenReturn(Optional.of(current));

        assertThrows(CareOtpInvalidException.class, () -> service.verify(actor, linkId, "123456"));
        verify(challenges, never()).saveAndFlush(any());
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void verifyConfirmsLinkWhenCurrentChallengeMatchesCode() {
        allowPatient();
        stubPendingLinkOwnedBy(depAccountId);
        var current = challenge(depAccountId);
        when(challenges.findCurrentForUpdate(linkId)).thenReturn(Optional.of(current));
        when(otp.matches("123456", "hash-123456")).thenReturn(true);
        var result = service.verify(actor, linkId, "123456");
        assertEquals("LINKED", result.result());
        assertEquals(NOW, current.getConsumedAt());
        verify(links).saveAndFlush(any());
    }

    @Test
    void resendRejectsLinkWhoseDependentHasNoRecipientEmail() {
        allowPatient();
        stubPendingLinkOwnedBy(depAccountId);
        assertEquals("CARE_LINK_NOT_AVAILABLE", assertThrows(CareRelationshipException.class,
                () -> service.resend(actor, linkId)).getMessage());
    }

    @Test
    void resendRejectsWhenConfirmingAccountIsNotTheDependentAccount() {
        allowPatient();
        stubPendingLinkOwnedBy(UUID.randomUUID());
        stubRecipient(PARENT_EMAIL);
        assertEquals("CARE_LINK_NOT_AVAILABLE", assertThrows(CareRelationshipException.class,
                () -> service.resend(actor, linkId)).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"recipient", "link"})
    void resendRejectsWhenRateLimitIsReached(String window) {
        allowPatient();
        stubPendingLinkOwnedBy(depAccountId);
        stubRecipient(PARENT_EMAIL);
        if (window.equals("recipient")) {
            when(challenges.countRecipientSince(any(), any())).thenReturn(5L);
        } else {
            when(challenges.countLinkSince(any(), any())).thenReturn(5L);
        }
        assertEquals("RATE_LIMIT_EXCEEDED", assertThrows(CareRelationshipException.class,
                () -> service.resend(actor, linkId)).getMessage());
    }

    @Test
    void resendSupersedesPreviousChallengeAndSendsNewCode() {
        allowPatient();
        stubPendingLinkOwnedBy(depAccountId);
        stubRecipient(PARENT_EMAIL);
        stubOtpDelivery();
        var previous = challenge(depAccountId);
        when(challenges.findCurrentForUpdate(linkId)).thenReturn(Optional.of(previous));
        var result = service.resend(actor, linkId);
        assertEquals("OTP_SENT", result.result());
        assertEquals(NOW, previous.getSupersededAt());
        verify(email).sendVerificationCode(PARENT_EMAIL, "123456", depAccountId);
    }

    @Test
    void listRejectsInactivePatientWithAccessDenied() {
        when(patients.isActivePatient(actor)).thenReturn(false);
        assertEquals("ACCESS_DENIED", assertThrows(CareRelationshipException.class,
                () -> service.list(actor)).getMessage());
    }

    @Test
    void listReturnsSelfAndMapsOnlyRelevantLinkStatuses() {
        allowPatient();
        when(profiles.findProfile(ownProfileId)).thenReturn(Optional.of(profile(ownProfileId, actor, null, null)));
        var confirmedDep = UUID.randomUUID();
        var otpDep = UUID.randomUUID();
        var counterDep = UUID.randomUUID();
        when(profiles.findProfile(confirmedDep)).thenReturn(Optional.of(profile(confirmedDep, null, null, null)));
        when(profiles.findProfile(otpDep)).thenReturn(Optional.of(profile(otpDep, null, null, null)));
        when(profiles.findProfile(counterDep)).thenReturn(Optional.of(profile(counterDep, null, null, null)));
        var legacy = new CareRelationship(UUID.randomUUID(), actor, UUID.randomUUID(), null, State.REQUESTED, NOW,
                null, null, null);
        when(links.findAllByRequesterAccountIdOrderByRequestedAtAscIdAsc(actor)).thenReturn(List.of(
                CareRelationshipMapper.toEntity(link(UUID.randomUUID(), actor, confirmedDep, actor, State.CONFIRMED, Relationship.PARENT, null)),
                CareRelationshipMapper.toEntity(link(UUID.randomUUID(), actor, UUID.randomUUID(), actor, State.REVOKED, Relationship.SIBLING, null)),
                CareRelationshipMapper.toEntity(legacy),
                CareRelationshipMapper.toEntity(link(UUID.randomUUID(), actor, otpDep, null, State.REQUESTED, Relationship.CHILD, PendingReason.OTP)),
                CareRelationshipMapper.toEntity(link(UUID.randomUUID(), actor, counterDep, null, State.REQUESTED, Relationship.SPOUSE, PendingReason.COUNTER)),
                CareRelationshipMapper.toEntity(link(UUID.randomUUID(), actor, UUID.randomUUID(), null, State.REQUESTED, Relationship.CHILD, null))));

        var items = service.list(actor).items();

        assertEquals(List.of("SELF", "PARENT", "CHILD", "SPOUSE"), items.stream().map(ProfileItem::relationship).toList());
        assertEquals(List.of("CONFIRMED", "CONFIRMED", "PENDING_OTP", "PENDING_COUNTER"),
                items.stream().map(ProfileItem::linkStatus).toList());
    }

    @Test
    void detailReturnsConfirmedDependentProfileAndRejectsNullOrUnlinked() {
        allowPatient();
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.detail(actor, null)).getMessage());
        when(links.findFirstByRequesterAccountIdAndDependentPatientProfileIdAndStateIn(eq(actor), eq(depProfileId), any()))
                .thenReturn(Optional.empty());
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.detail(actor, depProfileId)).getMessage());

        stubConfirmedDependent();
        when(profiles.findProfile(depProfileId)).thenReturn(Optional.of(profile(depProfileId, depAccountId, "CCCD", "012345678901")));
        var result = service.detail(actor, depProfileId);
        assertEquals(depProfileId, result.patientProfileId());
        assertEquals("Trần Minh Anh", result.fullName());
        assertTrue(result.hasAccount());
    }

    @Test
    void updateRejectsMissingRequestWithValidationFailed() {
        allowPatient();
        stubConfirmedDependent();
        assertEquals("VALIDATION_FAILED", assertThrows(CareRelationshipException.class,
                () -> service.update(actor, depProfileId, null)).getMessage());
    }

    @Test
    void updateRejectsMissingProfileIdWithCareLinkNotFound() {
        allowPatient();
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.update(actor, null, updateRequest(null))).getMessage());
    }

    @Test
    void updateRejectsAnonymousActorWithAccessDenied() {
        assertEquals("ACCESS_DENIED", assertThrows(CareRelationshipException.class,
                () -> service.update(null, depProfileId, updateRequest(null))).getMessage());
    }

    @Test
    void updateRejectsInactivePatientWithAccessDenied() {
        when(patients.isActivePatient(actor)).thenReturn(false);
        assertEquals("ACCESS_DENIED", assertThrows(CareRelationshipException.class,
                () -> service.update(actor, depProfileId, updateRequest(null))).getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateSavesFieldsAndReturnsRefreshedProfile(boolean linkedAccount) {
        allowPatient();
        stubConfirmedDependent();
        var current = linkedAccount
                ? new ProfileRecord(depProfileId, depAccountId, "Trần Minh Anh", "+84912345678", "PATIENT",
                        LocalDate.of(2015, 5, 1), "FEMALE", "Hà Nội", null, null, 3L)
                : new ProfileRecord(depProfileId, null, "Trần Minh Anh", null, null,
                        LocalDate.of(2015, 5, 1), "FEMALE", "Hà Nội", null, null, 3L);
        when(profiles.findProfile(depProfileId)).thenReturn(Optional.of(current));
        when(profiles.update(any(), eq(3L))).thenReturn(profile(depProfileId, null, null, null));

        var result = service.update(actor, depProfileId, updateRequest(linkedAccount ? "0912345678" : null));

        assertEquals(depProfileId, result.patientProfileId());
        verify(profiles).update(any(), eq(3L));
    }

    @Test
    void unlinkRevokesConfirmedRelationshipAndRejectsNullOrUnlinkedProfile() {
        allowPatient();
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.unlink(actor, null)).getMessage());

        stubConfirmedDependent();
        service.unlink(actor, depProfileId);

        var captor = ArgumentCaptor.forClass(CareRelationshipJpaEntity.class);
        verify(links).saveAndFlush(captor.capture());
        assertEquals(State.REVOKED, captor.getValue().getState());
        assertEquals(actor, captor.getValue().getRevokedByAccountId());
        assertEquals(NOW, captor.getValue().getRevokedAt());
    }

    @Test
    void revokeRevokesConfirmedLinkAndRejectsUnconfirmedOrMissingLink() {
        allowPatient();
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.revoke(actor, null)).getMessage());

        when(links.findById(linkId)).thenReturn(Optional.of(CareRelationshipMapper.toEntity(pendingOtp(linkId))));
        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.revoke(actor, linkId)).getMessage());

        when(links.findById(linkId)).thenReturn(Optional.of(CareRelationshipMapper.toEntity(
                link(linkId, actor, depProfileId, depAccountId, State.CONFIRMED, Relationship.CHILD, null))));
        var revoked = service.revoke(actor, linkId);
        assertEquals(State.REVOKED, revoked.state());
        assertEquals(actor, revoked.revokedByAccountId());
        verify(links).saveAndFlush(any());
    }

    @Test
    void pendingRejectsMissingActorWithAccessDenied() {
        assertEquals("ACCESS_DENIED", assertThrows(CareRelationshipException.class,
                () -> service.pending(null, null)).getMessage());
    }

    @Test
    void pendingRejectsInactiveReceptionistWithAccessDenied() {
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(false);
        assertEquals("ACCESS_DENIED", assertThrows(CareRelationshipException.class,
                () -> service.pending(actor, null)).getMessage());
    }

    @Test
    void pendingFiltersByNormalizedNameOrNationalIdAndRejectsOverlongQuery() {
        UUID receptionist = UUID.randomUUID();
        when(receptionists.isActiveReceptionistAccount(receptionist)).thenReturn(true);

        assertEquals("VALIDATION_FAILED", assertThrows(CareRelationshipException.class,
                () -> service.pending(receptionist, "a".repeat(101))).getMessage());
        verifyNoInteractions(links);

        UUID otherDep = UUID.randomUUID();
        UUID thirdDep = UUID.randomUUID();
        var link1 = link(UUID.randomUUID(), actor, depProfileId, null, State.REQUESTED, Relationship.CHILD, PendingReason.COUNTER);
        var link2 = link(UUID.randomUUID(), actor, otherDep, null, State.REQUESTED, Relationship.PARENT, PendingReason.COUNTER);
        var link3 = link(UUID.randomUUID(), actor, thirdDep, null, State.REQUESTED, Relationship.SPOUSE, PendingReason.COUNTER);
        when(links.findAllByStateAndPendingReasonOrderByRequestedAtAscIdAsc(State.REQUESTED, PendingReason.COUNTER))
                .thenReturn(List.of(CareRelationshipMapper.toEntity(link1), CareRelationshipMapper.toEntity(link2),
                        CareRelationshipMapper.toEntity(link3)));
        when(profiles.findProfile(depProfileId)).thenReturn(Optional.of(profile(depProfileId, null, "CCCD", "012345678901")));
        when(profiles.findProfile(otherDep)).thenReturn(Optional.of(new ProfileRecord(otherDep, null, "Lê Hoàng Nam",
                null, null, LocalDate.of(1992, 3, 4), "MALE", "Huế", "999888777", "CCCD", 1L)));
        when(profiles.findProfile(thirdDep)).thenReturn(Optional.of(new ProfileRecord(thirdDep, null, "Phạm Thu Hà",
                null, null, LocalDate.of(1995, 7, 8), "FEMALE", "Đà Nẵng", null, null, 1L)));

        assertEquals(3, service.pending(receptionist, "  ").items().size());
        var byName = service.pending(receptionist, "  trần   minh ").items();
        assertEquals(1, byName.size());
        assertEquals(depProfileId, byName.getFirst().patientProfileId());
        var byNationalId = service.pending(receptionist, "999888").items();
        assertEquals(1, byNationalId.size());
        assertEquals(otherDep, byNationalId.getFirst().patientProfileId());
    }

    @Test
    void confirmCounterVerifiesPendingCounterLinkAndRequiresIdentityCheck() {
        UUID receptionist = UUID.randomUUID();
        when(receptionists.isActiveReceptionistAccount(receptionist)).thenReturn(true);

        assertEquals("VALIDATION_FAILED", assertThrows(CareRelationshipException.class,
                () -> service.confirmCounter(receptionist, linkId, Boolean.FALSE)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(CareRelationshipException.class,
                () -> service.confirmCounter(receptionist, linkId, null)).getMessage());
        verifyNoInteractions(guard, links);

        when(links.findById(linkId)).thenReturn(Optional.of(CareRelationshipMapper.toEntity(
                link(linkId, actor, depProfileId, null, State.REQUESTED, Relationship.CHILD, PendingReason.COUNTER))));
        when(profiles.findProfile(depProfileId)).thenReturn(Optional.of(profile(depProfileId, null, "PASSPORT", "P123")));

        var result = service.confirmCounter(receptionist, linkId, Boolean.TRUE);
        assertEquals("LINKED", result.result());
        var captor = ArgumentCaptor.forClass(CareRelationshipJpaEntity.class);
        verify(links).saveAndFlush(captor.capture());
        assertEquals(State.CONFIRMED, captor.getValue().getState());
        assertEquals(VerificationMethod.COUNTER_CHECK, captor.getValue().getVerificationMethod());
        assertEquals(receptionist, captor.getValue().getVerifiedByAccountId());
    }

    @Test
    void rejectCounterRevokesPendingCounterLinkAndRejectsWrongPendingReason() {
        UUID receptionist = UUID.randomUUID();
        when(receptionists.isActiveReceptionistAccount(receptionist)).thenReturn(true);

        assertEquals("CARE_LINK_NOT_FOUND", assertThrows(CareRelationshipException.class,
                () -> service.rejectCounter(receptionist, null)).getMessage());

        when(links.findById(linkId)).thenReturn(Optional.of(CareRelationshipMapper.toEntity(pendingOtp(linkId))));
        assertEquals("CARE_LINK_NOT_AVAILABLE", assertThrows(CareRelationshipException.class,
                () -> service.rejectCounter(receptionist, linkId)).getMessage());

        when(links.findById(linkId)).thenReturn(Optional.of(CareRelationshipMapper.toEntity(
                link(linkId, actor, depProfileId, null, State.REQUESTED, Relationship.CHILD, PendingReason.COUNTER))));
        service.rejectCounter(receptionist, linkId);

        var captor = ArgumentCaptor.forClass(CareRelationshipJpaEntity.class);
        verify(links).saveAndFlush(captor.capture());
        assertEquals(State.REVOKED, captor.getValue().getState());
        assertEquals(receptionist, captor.getValue().getRevokedByAccountId());
    }

    private void allowPatient() {
        when(patients.isActivePatient(actor)).thenReturn(true);
        when(profiles.findPatientProfile(actor)).thenReturn(Optional.of(ownProfileId));
        when(auth.resend()).thenReturn(new AuthProperties.RateLimit(5, 900));
    }

    private void stubCandidate(ProfileRecord candidate) {
        when(profiles.matchDependent(any(), any(), any())).thenReturn(List.of(candidate));
    }

    private void stubRecipient(String recipientEmail) {
        when(accounts.find(depAccountId))
                .thenReturn(Optional.of(new MyAccountView(depAccountId, "PATIENT", recipientEmail, "Phụ huynh")));
    }

    private void stubConfirmedDependent() {
        when(links.findFirstByRequesterAccountIdAndDependentPatientProfileIdAndStateIn(eq(actor), eq(depProfileId), any()))
                .thenReturn(Optional.of(CareRelationshipMapper.toEntity(
                        link(linkId, actor, depProfileId, depAccountId, State.CONFIRMED, Relationship.PARENT, null))));
    }

    private void stubPendingLinkOwnedBy(UUID confirmingAccountId) {
        when(links.findById(linkId)).thenReturn(Optional.of(CareRelationshipMapper.toEntity(
                link(linkId, actor, depProfileId, confirmingAccountId, State.REQUESTED, Relationship.CHILD, PendingReason.OTP))));
        when(profiles.findProfile(depProfileId))
                .thenReturn(Optional.of(profile(depProfileId, depAccountId, "CCCD", "012345678901")));
    }

    private void stubOtpDelivery() {
        when(otp.generate()).thenReturn("123456");
        when(otp.hash("123456")).thenReturn("hash-123456");
        when(otp.hash("DELIVERY_PENDING")).thenReturn("hash-pending");
        when(challenges.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(challenges.activateIfCurrent(any(), any(), eq("hash-123456"), any(), anyInt())).thenReturn(1);
        when(links.findById(any())).thenAnswer(invocation -> Optional.of(CareRelationshipMapper.toEntity(
                link(invocation.getArgument(0), actor, depProfileId, depAccountId, State.REQUESTED, Relationship.CHILD,
                        PendingReason.OTP))));
    }

    private static ProfileRecord profile(UUID id, UUID accountId, String documentType, String nationalId) {
        return new ProfileRecord(id, accountId, "Trần Minh Anh", null, null, LocalDate.of(2015, 5, 1), "FEMALE",
                "Hà Nội", nationalId, documentType, 1L);
    }

    private static AddDependentRequest addRequest(String documentType, String nationalId) {
        return new AddDependentRequest("Trần Minh Anh", LocalDate.of(2015, 5, 1), "FEMALE", nationalId, documentType,
                Relationship.CHILD);
    }

    private static UpdateDependentRequest updateRequest(String phone) {
        return new UpdateDependentRequest("Trần Minh Anh", LocalDate.of(2015, 5, 1), "FEMALE", phone, "Hà Nội", null, null);
    }

    private static CareRelationship link(UUID id, UUID requester, UUID dependent, UUID confirming, State state,
            Relationship relationship, PendingReason pending) {
        return new CareRelationship(id, requester, dependent, confirming, state, NOW, null, null, null, relationship, null,
                pending, null, null);
    }

    private CareRelationship pendingOtp(UUID id) {
        return link(id, actor, depProfileId, depAccountId, State.REQUESTED, Relationship.CHILD, PendingReason.OTP);
    }

    private static VerificationCodeJpaEntity challenge(UUID accountId) {
        var challenge = new VerificationCodeJpaEntity();
        challenge.setAccountId(accountId);
        challenge.setCodeHash("hash-123456");
        challenge.setExpiresAt(NOW.plusSeconds(600));
        return challenge;
    }
}
