package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthSessionRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAccessServiceImpl implements AccountAccessService {
    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;

    public AccountAccessServiceImpl(AccountRepository accounts, AuthSessionRepository sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public AccessState evaluate(AuthenticatedAccount actor) {
        var account = accounts.findAccessSnapshotById(actor.accountId()).orElse(null);
        if (account == null) {
            return AccessState.INVALID_SESSION;
        }
        // Staff management revocation applies equally to legacy email and username accounts.
        if (account.accountType() == AccountType.ADMIN || account.accountType() == AccountType.RECEPTIONIST
                || account.accountType() == AccountType.DOCTOR || account.accountType() == AccountType.LAB_TECHNICIAN
                || account.accountType() == AccountType.IMAGING_TECHNICIAN) {
            boolean validSession = sessions.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(
                    actor.sessionId(), actor.accountId(), Instant.now());
            if (account.status() != AccountStatus.ACTIVE || !validSession) {
                return AccessState.INVALID_SESSION;
            }
        }
        return account.mustChangePassword() ? AccessState.PASSWORD_CHANGE_REQUIRED : AccessState.ALLOWED;
    }
}
