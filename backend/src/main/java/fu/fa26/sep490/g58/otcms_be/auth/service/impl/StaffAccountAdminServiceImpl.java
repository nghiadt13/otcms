package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.StaffAccountAdminException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.StaffAccountAdminException.Reason;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.StaffAccountDtos;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountManagementAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountAdminService;
import fu.fa26.sep490.g58.otcms_be.auth.validation.AdministratorUsernamePolicy;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffProfileAccountQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffProfileAccountQueryService.StaffProfileSummary;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** R3 UC07–UC09: account management and transaction boundaries owned by Auth. */
@Service
public class StaffAccountAdminServiceImpl implements StaffAccountAdminService {
    private static final Set<AccountType> MANAGED = Set.of(AccountType.RECEPTIONIST, AccountType.DOCTOR,
            AccountType.IMAGING_TECHNICIAN, AccountType.LAB_TECHNICIAN);
    private final AccountRepository accounts;
    private final AuthConcurrencyRepository concurrency;
    private final AccountManagementAuditRepository audits;
    private final PasswordEncoder encoder;
    private final AdministratorPasswordIssuer passwords;
    private final PasswordResetChallengeRepository challenges;
    private final AuthSessionService sessions;
    private final StaffProfileAccountQueryService profiles;

    public StaffAccountAdminServiceImpl(AccountRepository accounts, AuthConcurrencyRepository concurrency,
            AccountManagementAuditRepository audits, PasswordEncoder encoder, AdministratorPasswordIssuer passwords,
            PasswordResetChallengeRepository challenges, AuthSessionService sessions,
            StaffProfileAccountQueryService profiles) {
        this.accounts = accounts;
        this.concurrency = concurrency;
        this.audits = audits;
        this.encoder = encoder;
        this.passwords = passwords;
        this.challenges = challenges;
        this.sessions = sessions;
        this.profiles = profiles;
    }

    @Override
    @Transactional
    public StaffAccountDtos.Created create(AuthenticatedAccount actor, String role, String username,
            String initialPassword, String correlationId) {
        requireAdmin(actor);
        AccountType type = role(role, Reason.CREATE_INPUT);
        if (!AdministratorUsernamePolicy.isValid(username) || !PasswordPolicy.isValid(initialPassword))
            throw failure(Reason.CREATE_INPUT);
        concurrency.lock("administrator-username:" + username);
        if (accounts.existsByUsername(username)) throw failure(Reason.USERNAME_TAKEN);
        Instant now = Instant.now();
        var account = new AccountJpaEntity();
        account.setUsername(username);
        account.setAccountType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode(initialPassword));
        account.setMustChangePassword(true);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "uk_accounts_username".equals(violation.getConstraintName()))
                    throw failure(Reason.USERNAME_TAKEN);
            }
            throw failure(Reason.OPERATION_FAILED);
        }
        audit(actor, account, "CREATE", correlationId);
        return new StaffAccountDtos.Created(account.getId(), account.getUsername(), externalRole(type), "ACTIVE", true);
    }

    @Override
    @Transactional(readOnly = true)
    public StaffAccountDtos.Page list(AuthenticatedAccount actor, String query, String role, String status,
            int page, int size) {
        requireAdmin(actor);
        if (page < 0 || size < 1 || size > 100 || (query != null && query.length() > 100))
            throw failure(Reason.SEARCH_INPUT);
        AccountType type = role == null ? null : role(role, Reason.SEARCH_INPUT);
        Boolean active = switch (status == null ? "" : status) {
            case "" -> null;
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            default -> throw failure(Reason.SEARCH_INPUT);
        };
        String term = query == null ? "" : query.trim();
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = accounts.searchManagedStaff(MANAGED, type, active, pattern,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<UUID, StaffProfileSummary> summaries = profiles.findByAccountIds(result.getContent().stream()
                .map(AccountJpaEntity::getId).collect(Collectors.toSet()));
        return new StaffAccountDtos.Page(result.stream().map(a -> item(a, summaries.get(a.getId()))).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public StaffAccountDtos.Item detail(AuthenticatedAccount actor, UUID accountId) {
        requireAdmin(actor);
        if (accountId == null) throw failure(Reason.TARGET_UNAVAILABLE);
        return item(target(accounts.findById(accountId).orElse(null)));
    }

    @Override
    @Transactional
    public StaffAccountDtos.Item activate(AuthenticatedAccount actor, UUID accountId, String correlationId) {
        requireAdmin(actor);
        var account = lockedTarget(accountId);
        if (account.getStatus() != AccountStatus.DISABLED) throw failure(Reason.STATUS_UNCHANGED);
        account.setStatus(AccountStatus.ACTIVE);
        account.setFailedCredentialCount(0);
        account.setLockedUntil(null);
        account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        audit(actor, account, "ACTIVATE", correlationId);
        return item(account);
    }

    @Override
    @Transactional
    public StaffAccountDtos.Item deactivate(AuthenticatedAccount actor, UUID accountId, String correlationId) {
        requireAdmin(actor);
        var account = lockedTarget(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) throw failure(Reason.STATUS_UNCHANGED);
        account.setStatus(AccountStatus.DISABLED);
        account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        sessions.revokeAll(accountId, "ACCOUNT_DEACTIVATED");
        audit(actor, account, "DEACTIVATE", correlationId);
        return item(account);
    }

    @Override
    @Transactional
    public StaffAccountDtos.Reissued reissuePassword(AuthenticatedAccount actor, UUID accountId, String correlationId) {
        requireAdmin(actor);
        var account = lockedTarget(accountId);
        String issued = passwords.issue(account);
        account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        challenges.supersedeCurrent(accountId, Instant.now());
        sessions.revokeAll(accountId);
        audit(actor, account, "REISSUE_PASSWORD", correlationId);
        return new StaffAccountDtos.Reissued(accountId, issued, true);
    }

    private void requireAdmin(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.ADMIN
                || !accounts.existsByIdAndAccountTypeAndStatus(
                actor.accountId(), AccountType.ADMIN, AccountStatus.ACTIVE)) throw failure(Reason.ACTOR_DENIED);
    }

    private AccountJpaEntity lockedTarget(UUID accountId) {
        if (accountId == null) throw failure(Reason.TARGET_UNAVAILABLE);
        return target(accounts.findByIdForUpdate(accountId).orElse(null));
    }

    private AccountJpaEntity target(AccountJpaEntity account) {
        if (account == null || !MANAGED.contains(account.getAccountType())) throw failure(Reason.TARGET_UNAVAILABLE);
        return account;
    }

    private AccountType role(String value, Reason reason) {
        if (value == null) throw failure(reason);
        return switch (value) {
            case "RECEPTIONIST" -> AccountType.RECEPTIONIST;
            case "ORTHOPEDIC_DOCTOR" -> AccountType.DOCTOR;
            case "IMAGING_TECHNICIAN" -> AccountType.IMAGING_TECHNICIAN;
            case "LAB_TECHNICIAN" -> AccountType.LAB_TECHNICIAN;
            default -> throw failure(reason);
        };
    }

    private String externalRole(AccountType type) { return type == AccountType.DOCTOR ? "ORTHOPEDIC_DOCTOR" : type.name(); }

    private StaffAccountDtos.Item item(AccountJpaEntity account) {
        return item(account, profiles.findByAccountIds(Set.of(account.getId())).get(account.getId()));
    }

    private StaffAccountDtos.Item item(AccountJpaEntity a, StaffProfileSummary profile) {
        return new StaffAccountDtos.Item(a.getId(), a.getUsername(), externalRole(a.getAccountType()),
                a.getStatus() == AccountStatus.ACTIVE ? "ACTIVE" : "INACTIVE", a.isMustChangePassword(),
                a.getCreatedAt(), profile);
    }

    private void audit(AuthenticatedAccount actor, AccountJpaEntity account, String action, String correlationId) {
        audits.saveAndFlush(new AccountManagementAuditJpaEntity(account.getId(), actor.accountId(), action,
                Instant.now(), correlationId));
    }

    private StaffAccountAdminException failure(Reason reason) { return new StaffAccountAdminException(reason); }
}
