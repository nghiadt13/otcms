package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffProfileAccountQueryService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffProfileAccountQueryServiceImpl implements StaffProfileAccountQueryService {
    private final StaffProfileRepository profiles;
    public StaffProfileAccountQueryServiceImpl(StaffProfileRepository profiles) { this.profiles = profiles; }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StaffProfileSummary> findByAccountIds(Set<UUID> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        return profiles.findAccountSummaries(accountIds).stream().collect(Collectors.toMap(
                StaffProfileRepository.AccountSummary::getAccountId,
                row -> new StaffProfileSummary(row.getId(), row.getDisplayName())));
    }
}
