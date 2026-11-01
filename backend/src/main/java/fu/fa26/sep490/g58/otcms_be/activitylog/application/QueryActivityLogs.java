package fu.fa26.sep490.g58.otcms_be.activitylog.application;
import fu.fa26.sep490.g58.otcms_be.activitylog.domain.ActivityLogEntry;
public interface QueryActivityLogs {
    ActivityLogReader.Page list(String from, String to, String action, String record, String actor, int page, int size);
    ActivityLogEntry detail(String id);
}
