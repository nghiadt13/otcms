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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    void ownerCanReadAdministratorDetailOnlyThroughRoleScopedLookup() {
        var account = account(AccountType.ADMIN);
        when(accounts.findByIdAndAccountType(account.getId(), AccountType.ADMIN)).thenReturn(Optional.of(account));
        var detail = service.detail(owner(), account.getId());
        assertEquals(account.getId(), detail.accountId());
        assertEquals("ADMIN", detail.accountType());
        verify(accounts, never()).findById(any());
    }

    @Test
    void missingAndEveryNonAdminTargetCannotBeExposed() {
        UUID missing = UUID.randomUUID();
        assertThrows(AdministratorAccountException.class, () -> service.detail(owner(), missing));
        for (AccountType type : AccountType.values()) {
            if (type == AccountType.ADMIN) continue;
            var account = account(type);
            when(accounts.findByIdAndAccountType(account.getId(), AccountType.ADMIN)).thenReturn(Optional.of(account));
            assertThrows(AdministratorAccountException.class, () -> service.detail(owner(), account.getId()));
            when(accounts.searchAdministrators(anyString(), isNull(), any()))
                    .thenReturn(new PageImpl<>(List.of(account)));
            assertThrows(AdministratorAccountException.class, () -> service.list(owner(), null, null, 0, 20));
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
        for (int[] paging : new int[][]{{-1, 20}, {0, 0}, {0, 51}}) {
            assertThrows(AdministratorAccountException.class,
                    () -> service.list(owner(), null, null, paging[0], paging[1]));
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
