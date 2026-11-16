package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.service.MyAccountQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class MyAccountQueryServiceImpl implements MyAccountQueryService {
    private final AccountRepository accounts;
    private final PatientProfileRepository profiles;

    public MyAccountQueryServiceImpl(AccountRepository accounts, PatientProfileRepository profiles) {
        this.accounts = accounts;
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MyAccountView> find(UUID accountId) {
        return accounts.findById(accountId).map(this::toView);
    }

    private MyAccountView toView(AccountJpaEntity account) {
        String email = account.getNormalizedEmail() == null ? "" : account.getNormalizedEmail();
        String displayName = Optional.ofNullable(account.getDisplayName()).filter(name -> !name.isBlank())
                .or(() -> profiles.findByAccountId(account.getId())
                .map(PatientProfileJpaEntity::getFullName)
                .filter(name -> !name.isBlank()))
                .or(() -> Optional.ofNullable(account.getFullName()).filter(name -> !name.isBlank()))
                .or(() -> Optional.ofNullable(account.getUsername()).filter(name -> !name.isBlank()))
                .orElseGet(() -> email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        boolean hasAvatar = account.getAvatarContentType() != null;
        var actions = new java.util.ArrayList<String>();
        boolean passwordSupported = account.getPasswordHash() != null && account.getStatus() == AccountStatus.ACTIVE;
        if (passwordSupported) actions.add("changePassword");
        if (!account.isMustChangePassword()) {
            actions.add("updateDisplayName");
            actions.add("updateAvatar");
            if (hasAvatar) actions.add("removeAvatar");
            if (account.getAccountType() == AccountType.PATIENT) actions.add("changeEmail");
        }
        return new MyAccountView(account.getId(), account.getAccountType().name(), email, displayName,
                account.getAccountType() == AccountType.PATIENT ? "PATIENT" : "STAFF",
                account.getUsername() == null ? "" : account.getUsername(),
                switch (account.getAccountType()) {
                    case OWNER -> "CLINIC_OWNER";
                    case DOCTOR -> "ORTHOPEDIC_DOCTOR";
                    default -> account.getAccountType().name();
                },
                hasAvatar ? "/api/v1/accounts/" + account.getId() + "/avatar" : null,
                account.isMustChangePassword(), account.getStatus() == AccountStatus.ACTIVE ? "ACTIVE" : "INACTIVE",
                java.util.List.copyOf(actions));
    }
}
