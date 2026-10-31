package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.exception.StaffAccountAdminException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.AdministratorPasswordIssuer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAccountAdminService {
    public record Issued(UUID accountId, String email, String fullName, String role, String temporaryPassword) { }
    private final AccountRepository accounts;
    private final AdministratorPasswordIssuer passwords;
    public StaffAccountAdminService(AccountRepository accounts, AdministratorPasswordIssuer passwords) {
        this.accounts = accounts; this.passwords = passwords;
    }

    @Transactional
    public Issued create(String rawEmail, String fullName, AccountType role) {
        if (role != AccountType.DOCTOR && role != AccountType.RECEPTIONIST
                && role != AccountType.LAB_TECHNICIAN && role != AccountType.IMAGING_TECHNICIAN)
            throw new StaffAccountAdminException("VALIDATION_FAILED");
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (accounts.existsByNormalizedEmail(email))
            throw new StaffAccountAdminException("EMAIL_ALREADY_EXISTS");
        AccountJpaEntity account = new AccountJpaEntity();
        account.setNormalizedEmail(email);
        account.setFullName(fullName.trim());
        account.setDisplayName(account.getFullName());
        account.setAccountType(role);
        account.setStatus(AccountStatus.ACTIVE);
        String issued = passwords.issue(account);
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(account.getCreatedAt());
        accounts.saveAndFlush(account);
        return new Issued(account.getId(), email, account.getFullName(),
                role == AccountType.DOCTOR ? "ORTHOPEDIC_DOCTOR" : role.name(), issued);
    }
}
