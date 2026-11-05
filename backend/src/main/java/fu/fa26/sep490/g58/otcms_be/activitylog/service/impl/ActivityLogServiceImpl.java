package fu.fa26.sep490.g58.otcms_be.activitylog.service.impl;

import fu.fa26.sep490.g58.otcms_be.activitylog.exception.ActivityLogOperationException;
import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogEntry;
import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogPage;
import fu.fa26.sep490.g58.otcms_be.activitylog.repository.ActivityLogRepository;
import fu.fa26.sep490.g58.otcms_be.activitylog.service.ActivityLogService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ActivityLogServiceImpl implements ActivityLogService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> CATEGORIES = Set.of("ACCOUNT", "STAFF_PROFILE", "STAFF_SHIFT", "ROOM",
        "SERVICE", "CONTENT", "ANNOUNCEMENT", "REVIEW", "PAYMENT", "RESULT_CORRECTION", "APPOINTMENT", "VISIT", "DIAGNOSTIC");
    private final ActivityLogRepository reader;
    public ActivityLogServiceImpl(ActivityLogRepository reader) {
        this.reader = reader;
    }
    @Override
    public ActivityLogPage list(String from, String to, String action, String record, String actor,
        int page, int size) {
        try {
            LocalDate start = clean(from).isEmpty() ? null : LocalDate.parse(from);
            LocalDate end = clean(to).isEmpty() ? null : LocalDate.parse(to);
            String category = clean(action);
            if ((start != null && end != null && start.isAfter(end)) || page < 0 || size < 1 || size > 100
                || (!category.isEmpty() && !CATEGORIES.contains(category))
                || clean(record).length() > 200 || clean(actor).length() > 200) {
                throw new IllegalArgumentException();
            }
            Math.multiplyExact(page, size);
            ActivityLogRepository.Page result = reader.list(new ActivityLogRepository.Criteria(start == null ? null : start.atStartOfDay(ZONE).toInstant(),
                end == null ? null : end.plusDays(1).atStartOfDay(ZONE).toInstant(),
                category, clean(record), clean(actor), page, size));
            return new ActivityLogPage(result.items(), result.page(), result.size(), result.totalItems(), result.totalPages());
        }
        catch (java.time.DateTimeException | IllegalArgumentException | ArithmeticException ex) {
            throw new ActivityLogOperationException("MSG-54");
        }
    }
    @Override
    public ActivityLogEntry detail(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z_]+:[0-9a-fA-F-]{36}")) {
            throw new ActivityLogOperationException("MSG-50");
        }
        return reader.find(id).orElseThrow(() -> new ActivityLogOperationException("MSG-50"));
    }
    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
