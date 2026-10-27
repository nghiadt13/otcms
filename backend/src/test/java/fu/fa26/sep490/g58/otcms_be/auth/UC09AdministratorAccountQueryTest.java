package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.mapper.AdministratorAccountMapper;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PasswordResetChallengeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.AdministratorPasswordIssuer;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UC09AdministratorAccountQueryTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final OwnerAdministratorService service =
            new OwnerAdministratorService(accounts,
                    mock(AuthConcurrencyRepository.class),
                    mock(AuthSessionService.class),
                    mock(PasswordResetChallengeRepository.class),
                    mock(AdministratorPasswordIssuer.class),
                    new AdministratorAccountMapper());

    @Test
    void ownerListsAdministratorsUsingDefaultPaginationWithoutSensitiveFields() {
        var account = account(AccountType.ADMIN);
        when(accounts.searchAdministrators(eq("%%"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(account), PageRequest.of(0, 20), 1));
        var result = service.list(owner(), null, null, 0, 20);
        assertEquals(1, result.getTotalElements());
        assertEquals(20, result.getSize());
        assertEquals("ADMIN", result.getContent().getFirst().accountType());
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(result.getContent());
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("temporaryPassword"));
        assertFalse(json.contains("mustChangePassword"));
        assertFalse(json.contains("failedCredentialCount"));
        assertFalse(json.contains("lockedUntil"));
        assertFalse(json.contains(account.getPasswordHash()));
    }

    @Test
    void ownerSearchesCaseInsensitivelyAndFiltersWithStablePagination() {
        when(accounts.searchAdministrators(eq("%admin%"), eq(AccountStatus.DISABLED), any()))
                .thenReturn(new PageImpl<>(List.of(account(AccountType.ADMIN)), PageRequest.of(1, 2), 5));
        var result = service.list(owner(), " AdMiN ", AccountStatus.DISABLED, 1, 2);
        assertEquals(1, result.getNumber());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(accounts).searchAdministrators(eq("%admin%"), eq(AccountStatus.DISABLED), paging.capture());
        assertEquals(2, paging.getValue().getOffset());
        assertEquals("createdAt: DESC,id: DESC", paging.getValue().getSort().toString().replace(", ", ","));
    }

    @Test
    void searchTreatsWildcardsAndQuotesAsLiteralInput() {
        when(accounts.searchAdministrators(anyString(), isNull(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        service.list(owner(), "a!%_'", null, 0, 20);
        verify(accounts).searchAdministrators(eq("%a!!!%!_'%"), isNull(), any());
    }

    @Test
    void blankQueryListsAdministratorsWithoutSearchTerm() {
        // Arrange
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(accounts.searchAdministrators("%%", null, pageable))
                .thenReturn(new PageImpl<>(List.of(account(AccountType.ADMIN)), pageable, 1));

        // Act
        var result = service.list(owner(), " \t ", null, 0, 20);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("ADMIN", result.getContent().getFirst().accountType());
        verify(accounts).searchAdministrators("%%", null, pageable);
    }

    @Test
    void ownerFiltersActiveAdministrators() {
        // Arrange
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(accounts.searchAdministrators("%%", AccountStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(account(AccountType.ADMIN)), pageable, 1));

        // Act
        var result = service.list(owner(), null, AccountStatus.ACTIVE, 0, 20);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("ACTIVE", result.getContent().getFirst().status());
        verify(accounts).searchAdministrators("%%", AccountStatus.ACTIVE, pageable);
    }

    @ParameterizedTest(name = "page zero accepts boundary size {0}")
    @ValueSource(ints = {1, 50})
    void firstPageAcceptsMinimumAndMaximumPageSize(int size) {
        // Arrange
        var pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(accounts.searchAdministrators("%%", null, pageable))
                .thenReturn(new PageImpl<>(List.of(account(AccountType.ADMIN)), pageable, 1));

        // Act
        var result = service.list(owner(), null, null, 0, size);

        // Assert
        assertEquals(0, result.getNumber());
        assertEquals(size, result.getSize());
        assertEquals(1, result.getNumberOfElements());
        assertEquals(1, result.getTotalElements());
        verify(accounts).searchAdministrators("%%", null, pageable);
    }

    @Test
    void ownerCanReadAdministratorDetailOnlyThroughRoleScopedLookup() {
        var account = account(AccountType.ADMIN);
        account.setNormalizedEmail("administrator@example.test");
        account.setUpdatedAt(Instant.EPOCH.plusSeconds(60));
        when(accounts.findByIdAndAccountType(account.getId(), AccountType.ADMIN)).thenReturn(Optional.of(account));
        var detail = service.detail(owner(), account.getId());
        assertEquals(account.getId(), detail.accountId());
        assertEquals("ADMIN", detail.accountType());
        assertEquals(account.getUsername(), detail.username());
        assertEquals(account.getNormalizedEmail(), detail.email());
        assertEquals(account.getStatus().name(), detail.status());
        assertEquals(account.getCreatedAt(), detail.createdAt());
        assertEquals(account.getUpdatedAt(), detail.updatedAt());
        verify(accounts).findByIdAndAccountType(account.getId(), AccountType.ADMIN);
        verify(accounts, never()).findById(any());
    }

    @Test
    void missingAndEveryNonAdminTargetCannotBeExposed() {
        UUID missing = UUID.randomUUID();
        assertEquals("ADMINISTRATOR_ACCOUNT_NOT_FOUND", assertThrows(AdministratorAccountException.class,
                () -> service.detail(owner(), missing)).getMessage());
        for (AccountType type : AccountType.values()) {
            if (type == AccountType.ADMIN) continue;
            var account = account(type);
            when(accounts.findByIdAndAccountType(account.getId(), AccountType.ADMIN)).thenReturn(Optional.of(account));
            assertEquals("ADMINISTRATOR_ACCOUNT_NOT_FOUND", assertThrows(AdministratorAccountException.class,
                    () -> service.detail(owner(), account.getId())).getMessage());
            when(accounts.searchAdministrators(anyString(), isNull(), any()))
                    .thenReturn(new PageImpl<>(List.of(account)));
            assertEquals("ADMINISTRATOR_ACCOUNT_NOT_FOUND", assertThrows(AdministratorAccountException.class,
                    () -> service.list(owner(), null, null, 0, 20)).getMessage());
        }
    }

    @Test
    void allNonOwnersAndAnonymousAreDeniedBeforeLookup() {
        for (AccountRole role : AccountRole.values()) {
            if (role == AccountRole.OWNER) continue;
            var actor = new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID());
            assertThrows(AccessDeniedException.class, () -> service.list(actor, null, null, 0, 20));
            assertThrows(AccessDeniedException.class, () -> service.detail(actor, UUID.randomUUID()));
        }
        assertThrows(AccessDeniedException.class, () -> service.list(null, null, null, 0, 20));
        assertThrows(AccessDeniedException.class, () -> service.detail(null, UUID.randomUUID()));
        verifyNoInteractions(accounts);
    }

    @Test
    void invalidPaginationIsRejectedBeforeQuery() {
        for (int[] paging : new int[][]{{-1, 20}, {0, -1}, {0, 0}, {0, 51}}) {
            var error = assertThrows(AdministratorAccountException.class,
                    () -> service.list(owner(), null, null, paging[0], paging[1]));
            assertEquals("VALIDATION_FAILED", error.getMessage());
        }
        verifyNoInteractions(accounts);
    }

    private AccountJpaEntity account(AccountType type) {
        var account = new AccountJpaEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("administrator");
        account.setAccountType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("sensitive-password-hash");
        account.setMustChangePassword(true);
        account.setFailedCredentialCount(3);
        account.setLockedUntil(Instant.now());
        account.setCreatedAt(Instant.EPOCH);
        account.setUpdatedAt(Instant.EPOCH);
        return account;
    }

    private AuthenticatedAccount owner() {
        return new AuthenticatedAccount(UUID.randomUUID(), AccountRole.OWNER, UUID.randomUUID());
    }
}
