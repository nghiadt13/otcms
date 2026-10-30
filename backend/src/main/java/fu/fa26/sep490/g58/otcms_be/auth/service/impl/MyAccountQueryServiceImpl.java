package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
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
        String email = account.getNormalizedEmail();
        String displayName = profiles.findByAccountId(account.getId())
                .map(PatientProfileJpaEntity::getFullName)
                .filter(name -> !name.isBlank())
                .or(() -> Optional.ofNullable(account.getFullName()).filter(name -> !name.isBlank()))
                .orElseGet(() -> email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        return new MyAccountView(account.getId(), account.getAccountType().name(), email, displayName);
    }
}
