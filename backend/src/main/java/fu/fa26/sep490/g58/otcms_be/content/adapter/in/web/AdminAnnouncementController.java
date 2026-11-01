package fu.fa26.sep490.g58.otcms_be.content.adapter.in.web;
import fu.fa26.sep490.g58.otcms_be.content.application.*;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/announcements")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminAnnouncementController {
    private final ManageClinicContent content;
    public AdminAnnouncementController(ManageClinicContent content) { this.content=content; }
    @GetMapping("/options") public ManageClinicContent.Options options() { return content.options(); }
    @GetMapping public Page<Map<String,Object>> list(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String audience,@RequestParam(defaultValue="") String from,
            @RequestParam(defaultValue="") String to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var result=content.announcements(q,audience,from,to,page,size,false);
        return new Page<>(result.items().stream().map(e -> ContentWebProjection.announcement(e,false)).toList(),
                result.page(),result.size(),result.totalItems(),result.totalPages());
    }
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID id) {
        return ContentWebProjection.announcement(content.announcement(id,false),true);
    }
    @PostMapping public ResponseEntity<Map<String,Object>> create(@RequestBody ManageClinicContent.AnnouncementInput input,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.status(201).body(ContentWebProjection.announcement(content.announce(input,actor==null?null:actor.accountId()),true));
    }
}
