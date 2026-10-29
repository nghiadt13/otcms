package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReceptionistAccountAccessServiceImpl implements ReceptionistAccountAccessService {
    private final AccountRepository accounts;

    public ReceptionistAccountAccessServiceImpl(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean isActiveReceptionistAccount(UUID accountId) {
        return accountId != null && accounts.existsByIdAndAccountTypeAndStatus(
                accountId, AccountType.RECEPTIONIST, AccountStatus.ACTIVE);
    }
}
