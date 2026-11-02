package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Exposes only the account-type decision; account status is intentionally not considered. */
@Service
public class DoctorAccountTypeQueryServiceImpl implements DoctorAccountTypeQueryService {
    private final AccountRepository accounts;

    public DoctorAccountTypeQueryServiceImpl(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean isDoctorAccount(UUID accountId) {
        return accountId != null && accounts.existsByIdAndAccountType(accountId, AccountType.DOCTOR);
    }
}
