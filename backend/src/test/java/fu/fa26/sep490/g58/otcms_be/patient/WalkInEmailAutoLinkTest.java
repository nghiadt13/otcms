package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.ConsentActorType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.EmailOwnerType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.VerificationCodeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.EmailOtpServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.PatientProfileDirectoryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.RegistrationServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientProfileException;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientProfileServiceImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkInEmailAutoLinkTest {

    @Test
    void createWalkInProfileNormalizesOptionalEmailAndOwner() {
        var directory = mock(PatientProfileDirectory.class);
        var receptionists = mock(ReceptionistAccountAccessService.class);
        var actor = UUID.randomUUID();
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(true);
        when(directory.duplicateExists(any(), any(), any())).thenReturn(false);
        when(directory.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        var service = new PatientProfileServiceImpl(directory, mock(CoordinationGuardService.class), receptionists);

        var created = service.create(actor, "Trần Văn An", "0912345678", "PATIENT",
                LocalDate.of(1990, 5, 10), "MALE", "123 Lê Lợi", null, null,
                "  An.Tran@Example.COM ", "patient");

        assertEquals("an.tran@example.com", created.email());
        assertEquals("PATIENT", created.emailOwnerType());
    }

    @Test
    void createWalkInProfileRejectsIncompleteOrMalformedEmailPair() {
        var directory = mock(PatientProfileDirectory.class);
        var receptionists = mock(ReceptionistAccountAccessService.class);
        var actor = UUID.randomUUID();
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(true);
        var service = new PatientProfileServiceImpl(directory, mock(CoordinationGuardService.class), receptionists);

        assertThrows(PatientProfileException.class, () -> service.create(actor, "Trần Văn An", "0912345678", "PATIENT",
                LocalDate.of(1990, 5, 10), "MALE", "123 Lê Lợi", null, null, "an@example.com", null));
        assertThrows(PatientProfileException.class, () -> service.create(actor, "Trần Văn An", "0912345678", "PATIENT",
                LocalDate.of(1990, 5, 10), "MALE", "123 Lê Lợi", null, null, null, "PATIENT"));
        assertThrows(PatientProfileException.class, () -> service.create(actor, "Trần Văn An", "0912345678", "PATIENT",
                LocalDate.of(1990, 5, 10), "MALE", "123 Lê Lợi", null, null, "not-an-email", "PATIENT"));
    }

    @Test
    void updateAllowsChangingEmailWhileUnlinkedAndRejectsWhenLinked() {
        var directory = mock(PatientProfileDirectory.class);
        var receptionists = mock(ReceptionistAccountAccessService.class);
        var actor = UUID.randomUUID();
        var profileId = UUID.randomUUID();
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(true);
        when(directory.update(any(), anyLong())).thenAnswer(inv -> inv.getArgument(0));
        var service = new PatientProfileServiceImpl(directory, mock(CoordinationGuardService.class), receptionists);

        var unlinked = new ProfileRecord(profileId, null, "Trần Văn An", "+84912345678", "PATIENT",
                LocalDate.of(1990, 5, 10), "MALE", "123 Lê Lợi", null, null, null, null, 0);
        when(directory.findProfile(profileId)).thenReturn(Optional.of(unlinked));

        var updated = service.update(actor, profileId, "Trần Văn An", "0912345678", "PATIENT",
                LocalDate.of(1990, 5, 10), "MALE", "123 Lê Lợi", null, null,
                "an@example.com", "PATIENT", 0L);
        assertEquals("an@example.com", updated.email());
        assertEquals("PATIENT", updated.emailOwnerType());

        var linked = new ProfileRecord(profileId, UUID.randomUUID(), "Trần Văn An", "+84912345678", "PATIENT",
                LocalDate.of(1990, 5, 10), "MALE", "123 Lê Lợi", null, null,
                "an@example.com", "PATIENT", 1);
        when(directory.findProfile(profileId)).thenReturn(Optional.of(linked));

        assertThrows(PatientProfileException.class, () -> service.update(actor, profileId, "Trần Văn An", "0912345678",
                "PATIENT", LocalDate.of(1990, 5, 10), "MALE", "123 Lê Lợi", null, null,
                "other@example.com", "PATIENT", 1L));

        var unchangedEmailUpdate = service.update(actor, profileId, "Trần Văn Bình", "0912345678", "PATIENT",
                LocalDate.of(1990, 5, 10), "MALE", "456 Nguyễn Huệ", null, null,
                "an@example.com", "PATIENT", 1L);
        assertEquals("Trần Văn Bình", unchangedEmailUpdate.fullName());
        assertEquals("an@example.com", unchangedEmailUpdate.email());
    }

    @Test
    void directoryAutoLinksOnlyWhenSingleUnlinkedOwnProfileMatchesAndAccountHasNone() {
        var repo = mock(PatientProfileRepository.class);
        var directory = new PatientProfileDirectoryServiceImpl(repo);
        var accountId = UUID.randomUUID();
        var profileId = UUID.randomUUID();

        var single = new PatientProfileJpaEntity();
        single.setId(profileId);
        single.setEmail("an@example.com");
        single.setEmailOwnerType(EmailOwnerType.PATIENT);

        when(repo.findByAccountId(accountId)).thenReturn(Optional.empty());
        when(repo.findUnlinkedByEmailAndOwnerForUpdate("an@example.com", EmailOwnerType.PATIENT, PageRequest.of(0, 2)))
                .thenReturn(List.of(single));

        var linkedId = directory.autoLinkUnlinkedOwnProfile(accountId, "An@Example.com");
        assertEquals(Optional.of(profileId), linkedId);
        assertEquals(accountId, single.getAccountId());
        verify(repo).saveAndFlush(single);

        // Two matching profiles -> ambiguous, must not link (BR-15)
        var second = new PatientProfileJpaEntity();
        second.setId(UUID.randomUUID());
        when(repo.findUnlinkedByEmailAndOwnerForUpdate("dup@example.com", EmailOwnerType.PATIENT, PageRequest.of(0, 2)))
                .thenReturn(List.of(new PatientProfileJpaEntity(), second));
        assertTrue(directory.autoLinkUnlinkedOwnProfile(accountId, "dup@example.com").isEmpty());

        // Account already has an own profile -> must not link (BR-15)
        when(repo.findByAccountId(accountId)).thenReturn(Optional.of(single));
        assertTrue(directory.autoLinkUnlinkedOwnProfile(accountId, "an@example.com").isEmpty());
    }

    @Test
    void verifyEmailTriggersAutoLinkAndAuditsWhenMatched() {
        var accounts = mock(AccountRepository.class);
        var challenges = mock(VerificationCodeRepository.class);
        var audits = mock(AuditLogWriter.class);
        var directory = mock(PatientProfileDirectory.class);
        var otp = new EmailOtpServiceImpl();
        var service = new RegistrationServiceImpl(accounts, challenges, audits, mock(AuditLogRepository.class),
                mock(EmailDeliveryService.class),
                new AuthProperties(null, new AuthProperties.RateLimit(5, 3600), new AuthProperties.RateLimit(3, 3600),
                        null, null, "v1", "v1", "test-secret"),
                mock(AuthConcurrencyRepository.class), otp, directory);

        UUID accountId = UUID.randomUUID();
        UUID walkInProfileId = UUID.randomUUID();
        AccountJpaEntity account = new AccountJpaEntity();
        account.setId(accountId);
        account.setNormalizedEmail("walkin@example.com");
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        account.setConsentActorType(ConsentActorType.PATIENT);
        account.setTermsVersion("v1");
        account.setPrivacyVersion("v1");

        VerificationCodeJpaEntity challenge = new VerificationCodeJpaEntity();
        challenge.setAccountId(accountId);
        challenge.setPurpose(VerificationPurpose.EMAIL_VERIFY);
        challenge.setCodeHash(otp.hash("123456"));
        challenge.setExpiresAt(Instant.now().plusSeconds(300));
        challenge.setFailedAttempts(0);

        when(accounts.findByNormalizedEmail("walkin@example.com")).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(challenges.findCurrentForUpdate(accountId, VerificationPurpose.EMAIL_VERIFY)).thenReturn(Optional.of(challenge));
        when(directory.autoLinkUnlinkedOwnProfile(accountId, "walkin@example.com")).thenReturn(Optional.of(walkInProfileId));

        service.verifyEmail("walkin@example.com", "123456");

        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(directory).autoLinkUnlinkedOwnProfile(accountId, "walkin@example.com");
        verify(audits).record(argThat(a -> "AUTO_LINK_WALK_IN_PROFILE".equals(a.action())
                && walkInProfileId.equals(a.entityId())
                && "LINKED".equals(a.outcome())));
    }

    @Test
    void createWalkInProfileAcceptsRelativeEmailOwner() {
        var directory = mock(PatientProfileDirectory.class);
        var receptionists = mock(ReceptionistAccountAccessService.class);
        var actor = UUID.randomUUID();
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(true);
        when(directory.duplicateExists(any(), any(), any())).thenReturn(false);
        when(directory.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        var service = new PatientProfileServiceImpl(directory, mock(CoordinationGuardService.class), receptionists);

        var created = service.create(actor, "Bé Minh", "0912345678", "GUARDIAN",
                LocalDate.of(2018, 5, 10), "MALE", "123 Lê Lợi", null, null,
                "Parent@Example.com", "RELATIVE");

        assertEquals("parent@example.com", created.email());
        assertEquals("RELATIVE", created.emailOwnerType());
    }
}
