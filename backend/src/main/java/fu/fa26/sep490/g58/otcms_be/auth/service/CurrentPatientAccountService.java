package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.exception.CurrentPatientAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CurrentPatientAccountService {
    private final AccountRepository accounts;
    private final PatientProfileDirectory patientProfiles;

    public CurrentPatientAccountService(AccountRepository accounts, PatientProfileDirectory patientProfiles) {
        this.accounts = accounts;
        this.patientProfiles = patientProfiles;
    }

    @Transactional(readOnly = true)
    public CurrentPatientAccount get(AuthenticatedAccount actor) {
        AccountJpaEntity account = patientAccount(actor.accountId());
        String displayName = account.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = patientProfiles.findPatientProfile(actor.accountId())
                    .flatMap(patientProfiles::findProfile)
                    .map(PatientProfileDirectory.ProfileRecord::fullName)
                    .orElse(null);
        }
        return toResponse(account, displayName);
    }

    @Transactional
    public CurrentPatientAccount updateDisplayName(AuthenticatedAccount actor, String requestedName) {
        String displayName = requestedName == null ? "" : requestedName.trim();
        int length = displayName.codePointCount(0, displayName.length());
        if (length < 2 || length > 100) throw new CurrentPatientAccountException("VALIDATION_FAILED");

        AccountJpaEntity account = patientAccount(actor.accountId());
        account.setDisplayName(displayName);
        accounts.save(account);
        return toResponse(account, displayName);
    }

    private AccountJpaEntity patientAccount(UUID accountId) {
        AccountJpaEntity account = accounts.findById(accountId)
                .orElseThrow(() -> new CurrentPatientAccountException("ACCOUNT_NOT_FOUND"));
        if (account.getAccountType() != AccountType.PATIENT) {
            throw new CurrentPatientAccountException("FORBIDDEN");
        }
        return account;
    }

    private CurrentPatientAccount toResponse(AccountJpaEntity account, String displayName) {
        return new CurrentPatientAccount(account.getId(), displayName, account.getNormalizedEmail(),
                account.getStatus().name());
    }

    public record CurrentPatientAccount(UUID id, String displayName, String email, String status) { }
}
