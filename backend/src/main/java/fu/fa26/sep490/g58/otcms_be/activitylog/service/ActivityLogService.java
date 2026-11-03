package fu.fa26.sep490.g58.otcms_be.activitylog.service;

import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogEntry;
import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogPage;

public interface ActivityLogService {
    ActivityLogPage list(String from, String to, String action, String record, String actor, int page, int size);
    ActivityLogEntry detail(String id);
}
