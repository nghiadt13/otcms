package fu.fa26.sep490.g58.otcms_be.auth.service.impl;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class PatientIdentityDirectoryServiceImpl implements PatientIdentityDirectory {
    private final AccountRepository accounts;
    public PatientIdentityDirectoryServiceImpl(AccountRepository accounts) { this.accounts = accounts; }
    @Override
    public boolean isActivePatient(UUID accountId) {
        return accountId != null && accounts.existsByIdAndAccountTypeAndStatus(accountId, AccountType.PATIENT, AccountStatus.ACTIVE);
    }
}
