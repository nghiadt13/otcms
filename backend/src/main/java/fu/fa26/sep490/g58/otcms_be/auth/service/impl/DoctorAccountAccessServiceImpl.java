package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DoctorAccountAccessServiceImpl implements DoctorAccountAccessService {
    private final AccountRepository accounts;

    public DoctorAccountAccessServiceImpl(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean isActiveDoctorAccount(UUID accountId) {
        return accountId != null && accounts.existsByIdAndAccountTypeAndStatus(
                accountId, AccountType.DOCTOR, AccountStatus.ACTIVE);
    }
}
