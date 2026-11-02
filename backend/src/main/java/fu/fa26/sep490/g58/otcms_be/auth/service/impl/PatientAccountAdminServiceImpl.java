package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.PatientAccountAdminException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.PatientAccountAdminRow;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.PatientAccountAdminResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.PatientAccountsPageResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientAccountAdminService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientAccountAdminServiceImpl implements PatientAccountAdminService {
    private final AccountRepository accounts;
    private final PatientProfileRepository profiles;
    private final AuthSessionService sessions;

    public PatientAccountAdminServiceImpl(AccountRepository accounts, PatientProfileRepository profiles,
            AuthSessionService sessions) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public PatientAccountsPageResponse list(AuthenticatedAccount actor, String query, String status,
            int page, int size) {
        requireAdmin(actor);
        if (page < 0 || size < 1 || size > 100 || (query != null && query.length() > 100))
            throw new PatientAccountAdminException("VALIDATION_FAILED");
        Boolean activeOnly = switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "" -> null;
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            default -> throw new PatientAccountAdminException("VALIDATION_FAILED");
        };
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Page<PatientAccountAdminRow> result = accounts.searchPatientAccounts(pattern, activeOnly,
                PageRequest.of(page, size, Sort.unsorted()));
        return new PatientAccountsPageResponse(result.getContent().stream().map(this::toListItem).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PatientAccountAdminResponse detail(AuthenticatedAccount actor, UUID accountId) {
        requireAdmin(actor);
        AccountJpaEntity account = patientAccount(accountId);
        String fullName = patientName(account);
        return response(account, fullName);
    }

    @Override
    @Transactional
    public PatientAccountAdminResponse activate(AuthenticatedAccount actor, UUID accountId) {
        requireAdmin(actor);
        AccountJpaEntity account = patientAccountForUpdate(accountId);
        if (account.getStatus() != AccountStatus.DISABLED)
            throw new PatientAccountAdminException("ACCOUNT_STATUS_UNCHANGED");
        account.setStatus(AccountStatus.ACTIVE);
        account.setFailedCredentialCount(0);
        account.setLockedUntil(null);
        account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        return response(account, patientName(account));
    }

    @Override
    @Transactional
    public PatientAccountAdminResponse deactivate(AuthenticatedAccount actor, UUID accountId) {
        requireAdmin(actor);
        AccountJpaEntity account = patientAccountForUpdate(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE)
            throw new PatientAccountAdminException("ACCOUNT_STATUS_UNCHANGED");
        account.setStatus(AccountStatus.DISABLED);
        account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        sessions.revokeAll(accountId, "ACCOUNT_DEACTIVATED");
        return response(account, patientName(account));
    }

    private String patientName(AccountJpaEntity account) {
        return profiles.findByAccountId(account.getId()).map(profile -> profile.getFullName())
                .orElse(account.getDisplayName() == null ? account.getFullName() : account.getDisplayName());
    }

    private PatientAccountAdminResponse toListItem(PatientAccountAdminRow row) {
        return new PatientAccountAdminResponse(row.id(), "PATIENT", null, row.email(), row.fullName(),
                displayStatus(row.status()), row.createdAt(), allowedActions(row.status()), null);
    }

    private PatientAccountAdminResponse response(AccountJpaEntity account, String fullName) {
        return new PatientAccountAdminResponse(account.getId(), "PATIENT", null,
                account.getNormalizedEmail(), fullName, displayStatus(account.getStatus()), account.getCreatedAt(),
                allowedActions(account.getStatus()), null);
    }

    private List<String> allowedActions(AccountStatus status) {
        return switch (status) {
            case ACTIVE -> List.of("deactivate");
            case DISABLED -> List.of("activate");
            default -> List.of();
        };
    }

    private String displayStatus(AccountStatus status) {
        return status == AccountStatus.ACTIVE ? "ACTIVE" : "INACTIVE";
    }

    private AccountJpaEntity patientAccount(UUID accountId) {
        if (accountId == null) throw new PatientAccountAdminException("PATIENT_ACCOUNT_NOT_FOUND");
        return accounts.findByIdAndAccountType(accountId, AccountType.PATIENT)
                .orElseThrow(() -> new PatientAccountAdminException("PATIENT_ACCOUNT_NOT_FOUND"));
    }

    private AccountJpaEntity patientAccountForUpdate(UUID accountId) {
        if (accountId == null) throw new PatientAccountAdminException("PATIENT_ACCOUNT_NOT_FOUND");
        return accounts.findByIdForUpdate(accountId).filter(account -> account.getAccountType() == AccountType.PATIENT)
                .orElseThrow(() -> new PatientAccountAdminException("PATIENT_ACCOUNT_NOT_FOUND"));
    }

    private void requireAdmin(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.ADMIN)
            throw new AccessDeniedException("Admin access required");
    }
}
