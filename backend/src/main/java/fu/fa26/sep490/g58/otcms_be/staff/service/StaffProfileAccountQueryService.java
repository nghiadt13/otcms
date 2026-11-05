package fu.fa26.sep490.g58.otcms_be.staff.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Public Staff boundary for account directories, without contact or clinical information. */
public interface StaffProfileAccountQueryService {
    record StaffProfileSummary(UUID id, String displayName) { }
    Map<UUID, StaffProfileSummary> findByAccountIds(Set<UUID> accountIds);
}
