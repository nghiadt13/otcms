package fu.fa26.sep490.g58.otcms_be.activitylog.repository;

import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaActivityLogRepositoryAdapter implements ActivityLogRepository {
    private final ActivityLogJpaRepository queries;

    public JpaActivityLogRepositoryAdapter(ActivityLogJpaRepository queries) {
        this.queries = queries;
    }

    @Override
    public Page list(Criteria criteria) {
        long total = queries.countSafe(criteria.from(), criteria.until(), criteria.category(),
                criteria.record(), criteria.actor());
        List<ActivityLogEntry> items = queries.findSafePage(criteria.from(), criteria.until(), criteria.category(),
                        criteria.record(), criteria.actor(), criteria.size(), (long) criteria.page() * criteria.size())
                .stream().map(JpaActivityLogRepositoryAdapter::toModel).toList();
        return new Page(items, criteria.page(), criteria.size(), total,
                (int) ((total + criteria.size() - 1) / criteria.size()));
    }

    @Override
    public Optional<ActivityLogEntry> find(String id) {
        return queries.findSafeById(id).map(JpaActivityLogRepositoryAdapter::toModel);
    }

    private static ActivityLogEntry toModel(ActivityLogJpaRepository.SafeActivityRow row) {
        return new ActivityLogEntry(row.getId(), row.getOccurredAt(), row.getActorUsername(), row.getActorRole(),
                row.getAction(), null, row.getRecordType(), row.getRecordLabel(), row.getCorrectionReason(),
                row.getRecordCode(), null);
    }
}
