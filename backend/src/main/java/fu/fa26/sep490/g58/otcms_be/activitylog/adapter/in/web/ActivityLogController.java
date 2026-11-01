package fu.fa26.sep490.g58.otcms_be.activitylog.adapter.in.web;
import fu.fa26.sep490.g58.otcms_be.activitylog.application.QueryActivityLogs;
import fu.fa26.sep490.g58.otcms_be.activitylog.domain.ActivityLogEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/activity-logs")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
public class ActivityLogController {
    private final QueryActivityLogs query;
    public ActivityLogController(QueryActivityLogs query) { this.query = query; }
    public record Item(String id, Instant occurredAt, String actorUsername, String actorRole,
            String action, String actionRole, String recordType, String recordLabel, String correctionReason,
            List<String> allowedActions) {
        static Item from(ActivityLogEntry e) { return new Item(e.id(), e.occurredAt(), e.actorUsername(), e.actorRole(),
                e.action(), e.actionRole(), e.recordType(), e.recordLabel(), e.correctionReason(), List.of("view")); }
    }
    public record Detail(String id, Instant occurredAt, String actorUsername, String actorRole,
            String action, String actionRole, String recordType, String recordLabel, String correctionReason,
            List<String> allowedActions, String recordCode, String systemNote) {
        static Detail from(ActivityLogEntry e) { return new Detail(e.id(), e.occurredAt(), e.actorUsername(), e.actorRole(),
                e.action(), e.actionRole(), e.recordType(), e.recordLabel(), e.correctionReason(), List.of("view"), e.recordCode(), e.systemNote()); }
    }
    public record Page(List<Item> items, int page, int size, long totalItems, int totalPages) { }
    @GetMapping public Page list(@RequestParam(defaultValue="") String from, @RequestParam(defaultValue="") String to,
            @RequestParam(defaultValue="") String action, @RequestParam(defaultValue="") String record,
            @RequestParam(defaultValue="") String actor, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        var result = query.list(from, to, action, record, actor, page, size);
        return new Page(result.items().stream().map(Item::from).toList(), result.page(), result.size(), result.totalItems(), result.totalPages());
    }
    @GetMapping("/{id}") public Detail detail(@PathVariable String id) { return Detail.from(query.detail(id)); }
}
