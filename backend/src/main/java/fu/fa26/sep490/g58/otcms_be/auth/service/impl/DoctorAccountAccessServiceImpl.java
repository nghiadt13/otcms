package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Exposes only the current Doctor access decision; no account attributes leave Auth. */
@Service
public class DoctorAccountAccessServiceImpl implements DoctorAccountAccessService {
    private final AccountRepository accounts;

    public DoctorAccountAccessServiceImpl(AccountRepository accounts) { this.accounts = accounts; }

    @Override public boolean isActiveDoctorAccount(UUID accountId) {
        if (accountId == null) return false;
        return accounts.existsByIdAndAccountTypeAndStatus(accountId, AccountType.DOCTOR, AccountStatus.ACTIVE);
    }
}
