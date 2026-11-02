package fu.fa26.sep490.g58.otcms_be.activitylog.repository;

import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActivityLogRepository {
    record Criteria(Instant from, Instant until, String category, String record, String actor, int page, int size) {
    }
    record Page(List<ActivityLogEntry> items, int page, int size, long totalItems, int totalPages) {
    }
    Page list(Criteria criteria);
    Optional<ActivityLogEntry> find(String id);
}
