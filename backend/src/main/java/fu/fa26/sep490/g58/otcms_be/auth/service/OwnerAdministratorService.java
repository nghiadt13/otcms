package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.mapper.AdministratorAccountMapper;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.AdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CreateAdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.ReissueAdministratorPasswordResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PasswordResetChallengeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.AdministratorPasswordIssuer;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC07–UC09: the Owner creates, manages and views Administrator accounts. */
@Service
public class OwnerAdministratorService {
    private final AccountRepository accounts;
    private final AuthConcurrencyRepository concurrency;
    private final AuthSessionService sessions;
    private final PasswordResetChallengeRepository resetChallenges;
    private final AdministratorPasswordIssuer passwords;
    private final AdministratorAccountMapper mapper;

    public OwnerAdministratorService(AccountRepository accounts, AuthConcurrencyRepository concurrency,
            AuthSessionService sessions, PasswordResetChallengeRepository resetChallenges,
            AdministratorPasswordIssuer passwords, AdministratorAccountMapper mapper) {
        this.accounts = accounts;
        this.concurrency = concurrency;
        this.sessions = sessions;
        this.resetChallenges = resetChallenges;
        this.passwords = passwords;
        this.mapper = mapper;
    }

    @Transactional
    public CreateAdministratorAccountResponse create(AuthenticatedAccount actor, String username) {
        requireOwner(actor);
        if (username == null || username.isBlank()) {
            throw new AdministratorAccountException("VALIDATION_FAILED");
        }
        concurrency.lock("administrator-username:" + username);
        if (accounts.existsByUsername(username)) {
            throw new AdministratorAccountException("USERNAME_ALREADY_EXISTS");
        }
        Instant now = Instant.now();
        AccountJpaEntity account = new AccountJpaEntity();
        account.setUsername(username);
        account.setAccountType(AccountType.ADMIN);
        account.setStatus(AccountStatus.ACTIVE);
        String temporaryPassword = passwords.issue(account);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        saveNewAdministrator(account);
        return new CreateAdministratorAccountResponse(account.getId(), username, temporaryPassword);
    }

    @Transactional
    public AdministratorAccountResponse activate(AuthenticatedAccount actor, UUID accountId) {
        AccountJpaEntity account = findAdministratorForUpdate(actor, accountId);
        account.setStatus(AccountStatus.ACTIVE);
        account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        return mapper.toResponse(account);
    }

    @Transactional
    public AdministratorAccountResponse deactivate(AuthenticatedAccount actor, UUID accountId) {
        AccountJpaEntity account = findAdministratorForUpdate(actor, accountId);
        account.setStatus(AccountStatus.DISABLED);
        account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        sessions.revokeAll(accountId, "ACCOUNT_DEACTIVATED");
        return mapper.toResponse(account);
    }

    @Transactional
    public ReissueAdministratorPasswordResponse reissuePassword(AuthenticatedAccount actor, UUID accountId) {
        AccountJpaEntity account = findAdministratorForUpdate(actor, accountId);
        String issued = passwords.issue(account);
        account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        resetChallenges.supersedeCurrent(accountId, Instant.now());
        sessions.revokeAll(accountId);
        return new ReissueAdministratorPasswordResponse(accountId, issued);
    }

    @Transactional(readOnly = true)
    public Page<AdministratorAccountResponse> list(AuthenticatedAccount actor, String query, AccountStatus status,
                                                  int page, int size) {
        requireOwner(actor);
        // Same zero-based page and 1..50 page-size convention as PatientProfileServiceImpl.
        if (page < 0 || size < 1 || size > 50) {
            throw new AdministratorAccountException("VALIDATION_FAILED");
        }
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return accounts.searchAdministrators(pattern, status, pageable)
                .map(account -> mapper.toResponse(requireAdministrator(account)));
    }

    @Transactional(readOnly = true)
    public AdministratorAccountResponse detail(AuthenticatedAccount actor, UUID accountId) {
        requireOwner(actor);
        var account = accounts.findByIdAndAccountType(accountId, AccountType.ADMIN).orElse(null);
        return mapper.toResponse(requireAdministrator(account));
    }

    private void requireOwner(AuthenticatedAccount actor) {
        if (actor == null || actor.accountRole() != AccountRole.OWNER) {
            throw new AccessDeniedException("Owner access required");
        }
    }

    private AccountJpaEntity requireAdministrator(AccountJpaEntity account) {
        if (account == null || account.getAccountType() != AccountType.ADMIN) {
            throw new AdministratorAccountException("ADMINISTRATOR_ACCOUNT_NOT_FOUND");
        }
        return account;
    }

    private AccountJpaEntity findAdministratorForUpdate(AuthenticatedAccount actor, UUID accountId) {
        requireOwner(actor);
        return requireAdministrator(accounts.findByIdForUpdate(accountId).orElse(null));
    }

    private void saveNewAdministrator(AccountJpaEntity account) {
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            // SQL exceptions may contain credential hashes; never attach them to client errors.
            if (hasUsernameConflict(exception)) {
                throw new AdministratorAccountException("USERNAME_ALREADY_EXISTS");
            }
            throw new AdministratorAccountException("ACCOUNT_CREATION_FAILED");
        }
    }

    private boolean hasUsernameConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_accounts_username".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
