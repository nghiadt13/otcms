package fu.fa26.sep490.g58.otcms_be.activitylog.controller;

import fu.fa26.sep490.g58.otcms_be.activitylog.model.dto.ActivityLogDtos.DetailResponse;
import fu.fa26.sep490.g58.otcms_be.activitylog.model.dto.ActivityLogDtos.ItemResponse;
import fu.fa26.sep490.g58.otcms_be.activitylog.model.dto.ActivityLogDtos.PageResponse;
import fu.fa26.sep490.g58.otcms_be.activitylog.service.ActivityLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/activity-logs")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
public class ActivityLogController {
    private final ActivityLogService query;
    public ActivityLogController(ActivityLogService query) {
        this.query = query;
    }
    @GetMapping public PageResponse list(@RequestParam(defaultValue="") String from, @RequestParam(defaultValue="") String to,
        @RequestParam(defaultValue="") String action, @RequestParam(defaultValue="") String record,
        @RequestParam(defaultValue="") String actor, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size) {
        var result = query.list(from, to, action, record, actor, page, size);
        return new PageResponse(result.items().stream().map(ItemResponse::from).toList(), result.page(), result.size(), result.totalItems(), result.totalPages());
    }
    @GetMapping("/{id}") public DetailResponse detail(@PathVariable String id) {
        return DetailResponse.from(query.detail(id));
    }
}
