package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAccountDirectoryImpl implements StaffAccountDirectory {
    private final AccountRepository accounts;
    public StaffAccountDirectoryImpl(AccountRepository accounts) { this.accounts = accounts; }

    @Override
    @Transactional(readOnly = true)
    public List<Entry> activeStaff() {
        return accounts.findAll().stream().filter(this::eligible).map(this::entry).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Entry> activeStaff(UUID id) {
        return accounts.findById(id).filter(this::eligible).map(this::entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Entry> staff(UUID id) {
        return accounts.findById(id).filter(a -> switch (a.getAccountType()) {
            case DOCTOR, RECEPTIONIST, LAB_TECHNICIAN, IMAGING_TECHNICIAN -> true;
            default -> false;
        }).map(this::entry);
    }

    private boolean eligible(AccountJpaEntity a) {
        return a.getStatus() == AccountStatus.ACTIVE && switch (a.getAccountType()) {
            case DOCTOR, RECEPTIONIST, LAB_TECHNICIAN, IMAGING_TECHNICIAN -> true;
            default -> false;
        };
    }
    private Entry entry(AccountJpaEntity a) {
        return new Entry(a.getId(), a.getUsername() == null ? a.getNormalizedEmail() : a.getUsername(),
                a.getFullName() == null ? a.getDisplayName() : a.getFullName(), a.getAccountType());
    }
}
