package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class NotificationRecipientDirectoryImpl implements NotificationRecipientDirectory {
    private final AccountRepository accounts;
    private final PatientProfileDirectory profiles;
    public NotificationRecipientDirectoryImpl(AccountRepository accounts, PatientProfileDirectory profiles) {
        this.accounts = accounts; this.profiles = profiles;
    }
    public boolean active(UUID id) {
        return accounts.findById(id).filter(a -> a.getStatus() == AccountStatus.ACTIVE).isPresent();
    }
    public Optional<UUID> patientAccount(UUID profileId) {
        return profiles.findProfileAccount(profileId).filter(id -> accounts.existsByIdAndAccountTypeAndStatus(
                id, AccountType.PATIENT, AccountStatus.ACTIVE));
    }
    public List<UUID> activeAccounts(AccountRole role) {
        return accounts.findByAccountTypeAndStatusOrderByFullNameAscIdAsc(AccountType.valueOf(role.name()), AccountStatus.ACTIVE)
                .stream().map(a -> a.getId()).toList();
    }
}
