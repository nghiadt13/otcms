package fu.fa26.sep490.g58.otcms_be.activitylog.model;

import java.util.List;

/** Service-level page result; persistence adapters must map into this contract. */
public record ActivityLogPage(List<ActivityLogEntry> items, int page, int size, long totalItems, int totalPages) {
}
