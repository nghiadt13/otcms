package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.StaffAccountAdminException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.StaffAccountAdminException.Reason;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffProfileAccountQueryService;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Kan27StaffAccountServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthConcurrencyRepository locks = mock(AuthConcurrencyRepository.class);
    private final AuditLogWriter audits = mock(AuditLogWriter.class);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final AdministratorPasswordIssuer issuer = spy(new AdministratorPasswordIssuer(new TemporaryPasswordGenerator(), encoder));
    private final VerificationCodeRepository resets = mock(VerificationCodeRepository.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final StaffProfileAccountQueryService profiles = mock(StaffProfileAccountQueryService.class);
    private final StaffAccountAdminServiceImpl service = new StaffAccountAdminServiceImpl(accounts, locks, audits,
            encoder, issuer, resets, sessions, profiles);
    private final AuthenticatedAccount admin = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, UUID.randomUUID());
    private final String correlation = UUID.randomUUID().toString();

    @BeforeEach void setup() {
        when(accounts.existsByIdAndAccountTypeAndStatus(admin.accountId(), AccountType.ADMIN, AccountStatus.ACTIVE)).thenReturn(true);
        when(accounts.saveAndFlush(any())).thenAnswer(invocation -> {
            AccountJpaEntity account = invocation.getArgument(0);
            if (account.getId() == null) account.setId(UUID.randomUUID());
            return account;
        });
        when(profiles.findByAccountIds(anySet())).thenReturn(Map.of());
    }

    @ParameterizedTest
    @CsvSource({"RECEPTIONIST,RECEPTIONIST", "ORTHOPEDIC_DOCTOR,DOCTOR", "LAB_TECHNICIAN,LAB_TECHNICIAN",
            "IMAGING_TECHNICIAN,IMAGING_TECHNICIAN"})
    void createUsesSuppliedPasswordAndNoFabricatedIdentity(String external, AccountType internal) {
        String supplied = " Abc12345 ";
        var response = service.create(admin, external, " Exact@User ", supplied, correlation);
        var account = savedAccount();
        assertEquals(internal, account.getAccountType());
        assertEquals(" Exact@User ", account.getUsername());
        assertNull(account.getNormalizedEmail());
        assertNull(account.getFullName());
        assertNull(account.getDisplayName());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertTrue(account.isMustChangePassword());
        assertTrue(encoder.matches(supplied, account.getPasswordHash()));
        assertFalse(encoder.matches(supplied.trim(), account.getPasswordHash()));
        assertNotEquals(supplied, account.getPasswordHash());
        assertEquals(external, response.role());
        assertEquals("ACTIVE", response.status());
        verify(issuer, never()).issue(any());
        verifyNoInteractions(profiles);
        assertAudit("CREATE", account.getId());
        var order = inOrder(locks, accounts);
        order.verify(locks).lock("administrator-username: Exact@User ");
        order.verify(accounts).existsByUsername(" Exact@User ");
        order.verify(accounts).saveAndFlush(any());
        stubTarget(account);
        assertEquals(external, service.detail(admin, account.getId()).role());
        assertNull(service.detail(admin, account.getId()).staffProfile());
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"PATIENT", "ADMIN", "OWNER", "DOCTOR", "CUSTOM", "orthopedic_doctor"})
    void rejectsUnmanagedRoles(String role) {
        assertReason(Reason.CREATE_INPUT, () -> service.create(admin, role, "valid", "Abc12345", correlation));
        verifyNoInteractions(locks, audits);
    }

    static Stream<Arguments> usernames() {
        return Stream.of(Arguments.of(null, false), Arguments.of("", false), Arguments.of("  ", false),
                Arguments.of("x".repeat(64), true), Arguments.of("x".repeat(65), false),
                Arguments.of("user\nname", false), Arguments.of("user\u007f", false),
                Arguments.of("user\uD800", false), Arguments.of("User@clinic", true), Arguments.of("UPPER", true));
    }
    @ParameterizedTest @MethodSource("usernames")
    void usernamePolicyPreservesExactCase(String username, boolean valid) {
        if (valid) assertEquals(username, service.create(admin, "RECEPTIONIST", username, "Abc12345", correlation).username());
        else assertReason(Reason.CREATE_INPUT, () -> service.create(admin, "RECEPTIONIST", username, "Abc12345", correlation));
    }

    static Stream<Arguments> passwords() {
        return Stream.of(Arguments.of(null, false), Arguments.of("Abc1234", false), Arguments.of("Abc12345", true),
                Arguments.of("a".repeat(64) + "1", false), Arguments.of("abcdefgh", false), Arguments.of("12345678", false),
                Arguments.of("é".repeat(35) + "12", true), Arguments.of("é".repeat(36) + "1", false),
                Arguments.of("a".repeat(63) + "1", true), Arguments.of("字1234567", true));
    }
    @ParameterizedTest @MethodSource("passwords")
    void usesKan25PasswordPolicy(String password, boolean valid) {
        if (valid) {
            service.create(admin, "RECEPTIONIST", "valid", password, correlation);
            assertTrue(encoder.matches(password, savedAccount().getPasswordHash()));
        } else assertReason(Reason.CREATE_INPUT, () -> service.create(admin, "RECEPTIONIST", "valid", password, correlation));
    }

    @Test void duplicateAndOnlyExactConstraintMapToConflict() {
        when(accounts.existsByUsername("same")).thenReturn(true);
        assertReason(Reason.USERNAME_TAKEN, () -> service.create(admin, "RECEPTIONIST", "same", "Abc12345", correlation));
        for (String constraint : List.of("uk_accounts_username", "unrelated_constraint")) {
            doThrow(new DataIntegrityViolationException("redacted",
                    new org.hibernate.exception.ConstraintViolationException("redacted", null, constraint)))
                    .when(accounts).saveAndFlush(any());
            assertReason(constraint.equals("uk_accounts_username") ? Reason.USERNAME_TAKEN : Reason.OPERATION_FAILED,
                    () -> service.create(admin, "RECEPTIONIST", "new", "Abc12345", correlation));
        }
        verifyNoInteractions(audits);
    }

    @Test void actorMustBePersistedActiveAdminForEveryOperation() {
        for (AuthenticatedAccount actor : Arrays.asList(null, new AuthenticatedAccount(null, AccountRole.ADMIN, null),
                new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, null),
                new AuthenticatedAccount(UUID.randomUUID(), AccountRole.OWNER, null))) {
            assertReason(Reason.ACTOR_DENIED, () -> service.create(actor, "RECEPTIONIST", "valid", "Abc12345", correlation));
            assertReason(Reason.ACTOR_DENIED, () -> service.list(actor, null, null, null, 0, 20));
            assertReason(Reason.ACTOR_DENIED, () -> service.detail(actor, UUID.randomUUID()));
            assertReason(Reason.ACTOR_DENIED, () -> service.activate(actor, UUID.randomUUID(), correlation));
            assertReason(Reason.ACTOR_DENIED, () -> service.deactivate(actor, UUID.randomUUID(), correlation));
            assertReason(Reason.ACTOR_DENIED, () -> service.reissuePassword(actor, UUID.randomUUID(), correlation));
        }
        verifyNoInteractions(audits, sessions);
    }

    @Test void listEscapesLiteralSearchMapsRolesAndBatchesProfilesOnce() {
        var doctor = account(AccountType.DOCTOR, AccountStatus.TEMPORARILY_LOCKED);
        var receptionist = account(AccountType.RECEPTIONIST, AccountStatus.ACTIVE);
        receptionist.setUsername(null);
        var summary = new StaffProfileAccountQueryService.StaffProfileSummary(UUID.randomUUID(), "Public label");
        when(profiles.findByAccountIds(Set.of(doctor.getId(), receptionist.getId()))).thenReturn(Map.of(doctor.getId(), summary));
        when(accounts.searchManagedStaff(anySet(), eq(AccountType.DOCTOR), eq(false), eq("%A!%!_!!%"), any()))
                .thenReturn(new PageImpl<>(List.of(doctor, receptionist)));
        var response = service.list(admin, " A%_! ", "ORTHOPEDIC_DOCTOR", "INACTIVE", 0, 20);
        assertEquals("ORTHOPEDIC_DOCTOR", response.items().getFirst().role());
        assertEquals("INACTIVE", response.items().getFirst().status());
        assertEquals(summary, response.items().getFirst().staffProfile());
        assertNull(response.items().getLast().staffProfile());
        assertNull(response.items().getLast().username());
        verify(profiles).findByAccountIds(Set.of(doctor.getId(), receptionist.getId()));
        var paging = ArgumentCaptor.forClass(Pageable.class);
        verify(accounts).searchManagedStaff(anySet(), any(), any(), anyString(), paging.capture());
        assertEquals(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                "createdAt", "id"), paging.getValue().getSort());
    }

    @Test void listEmptyAndInvalidCriteria() {
        when(accounts.searchManagedStaff(anySet(), isNull(), isNull(), eq("%%"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        assertTrue(service.list(admin, null, null, null, 0, 20).items().isEmpty());
        assertReason(Reason.SEARCH_INPUT, () -> service.list(admin, "x".repeat(101), null, null, 0, 20));
        assertReason(Reason.SEARCH_INPUT, () -> service.list(admin, null, "DOCTOR", null, 0, 20));
        assertReason(Reason.SEARCH_INPUT, () -> service.list(admin, null, null, "DISABLED", 0, 20));
        assertReason(Reason.SEARCH_INPUT, () -> service.list(admin, null, null, null, -1, 20));
        for (int size : new int[]{0, 101}) assertReason(Reason.SEARCH_INPUT,
                () -> service.list(admin, null, null, null, 0, size));
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"PATIENT", "ADMIN", "OWNER"})
    void outOfScopeAndMissingTargetsAreUnavailable(AccountType type) {
        var account = account(type, AccountStatus.ACTIVE);
        stubTarget(account);
        assertReason(Reason.TARGET_UNAVAILABLE, () -> service.detail(admin, account.getId()));
        assertReason(Reason.TARGET_UNAVAILABLE, () -> service.activate(admin, account.getId(), correlation));
        assertReason(Reason.TARGET_UNAVAILABLE, () -> service.deactivate(admin, account.getId(), correlation));
        assertReason(Reason.TARGET_UNAVAILABLE, () -> service.reissuePassword(admin, account.getId(), correlation));
        assertReason(Reason.TARGET_UNAVAILABLE, () -> service.detail(admin, UUID.randomUUID()));
        assertReason(Reason.TARGET_UNAVAILABLE, () -> service.reissuePassword(admin, UUID.randomUUID(), correlation));
        verifyNoInteractions(audits);
    }

    @Test void activationAndDeactivationAuditOnlyRealTransitions() {
        var account = account(AccountType.DOCTOR, AccountStatus.DISABLED);
        account.setFailedCredentialCount(4);
        account.setLockedUntil(Instant.now().plusSeconds(900));
        stubTarget(account);
        service.activate(admin, account.getId(), correlation);
        assertEquals(0, account.getFailedCredentialCount());
        assertNull(account.getLockedUntil());
        assertAudit("ACTIVATE", account.getId());
        assertReason(Reason.STATUS_UNCHANGED, () -> service.activate(admin, account.getId(), correlation));
        service.deactivate(admin, account.getId(), correlation);
        assertEquals(AccountStatus.DISABLED, account.getStatus());
        verify(sessions).revokeAll(account.getId(), "ACCOUNT_DEACTIVATED");
        assertReason(Reason.STATUS_UNCHANGED, () -> service.deactivate(admin, account.getId(), correlation));
        assertEquals(2, mockingDetails(audits).getInvocations().size());
        account.setStatus(AccountStatus.TEMPORARILY_LOCKED);
        assertReason(Reason.STATUS_UNCHANGED, () -> service.activate(admin, account.getId(), correlation));
        assertReason(Reason.STATUS_UNCHANGED, () -> service.deactivate(admin, account.getId(), correlation));
        verify(audits, times(2)).record(any());
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "DISABLED", "TEMPORARILY_LOCKED"})
    void reissueRevokesAndSupersedesButPreservesActualStatusAndLock(AccountStatus status) {
        var account = account(AccountType.DOCTOR, status);
        String old = new TemporaryPasswordGenerator().generate();
        account.setPasswordHash(encoder.encode(old));
        account.setFailedCredentialCount(4);
        Instant until = Instant.now().plusSeconds(900);
        account.setLockedUntil(until);
        stubTarget(account);
        var response = service.reissuePassword(admin, account.getId(), correlation);
        assertTrue(PasswordPolicy.isValid(response.temporaryPassword()));
        assertTrue(encoder.matches(response.temporaryPassword(), account.getPasswordHash()));
        assertFalse(encoder.matches(old, account.getPasswordHash()));
        assertTrue(account.isMustChangePassword());
        assertEquals(status, account.getStatus());
        assertEquals(4, account.getFailedCredentialCount());
        assertEquals(until, account.getLockedUntil());
        verify(resets).supersedeCurrent(eq(account.getId()), eq(VerificationPurpose.PASSWORD_RESET), any());
        verify(sessions).revokeAll(account.getId());
        assertAudit("REISSUE_PASSWORD", account.getId());
        assertFalse(response.toString().contains(response.temporaryPassword()));
    }

    @ParameterizedTest @EnumSource(AccountStatus.class)
    void everyOtherDatabaseStateRejectsStatusTransitionsWithoutAudit(AccountStatus state) {
        var account = account(AccountType.RECEPTIONIST, state);
        stubTarget(account);
        if (state != AccountStatus.DISABLED)
            assertReason(Reason.STATUS_UNCHANGED, () -> service.activate(admin, account.getId(), correlation));
        if (state != AccountStatus.ACTIVE)
            assertReason(Reason.STATUS_UNCHANGED, () -> service.deactivate(admin, account.getId(), correlation));
        verifyNoInteractions(audits, sessions);
    }

    private AccountJpaEntity savedAccount() {
        var captor = ArgumentCaptor.forClass(AccountJpaEntity.class);
        verify(accounts).saveAndFlush(captor.capture());
        return captor.getValue();
    }
    private void assertAudit(String action, UUID target) {
        // The correlation id is taken from the request MDC by AuditLogWriter, so it is not part of the entry.
        verify(audits).record(argThat(e -> e.action().equals(action) && e.entityId().equals(target)
                && e.actorAccountId().equals(admin.accountId()) && e.source().equals("account_management_audits")
                && e.category().equals("ACCOUNT") && e.recordType().equals("ACCOUNT")
                && e.recordLabel().equals(target.toString()) && e.visibleInActivityLog()));
    }
    private void assertReason(Reason reason, org.junit.jupiter.api.function.Executable action) {
        assertEquals(reason, assertThrows(StaffAccountAdminException.class, action).reason());
    }
    private void stubTarget(AccountJpaEntity account) {
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
    }
    private AccountJpaEntity account(AccountType type, AccountStatus status) {
        var account = new AccountJpaEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("ExactName");
        account.setAccountType(type);
        account.setStatus(status);
        account.setCreatedAt(Instant.now());
        return account;
    }
}
