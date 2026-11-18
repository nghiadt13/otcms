package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ServiceWorklistDtos;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceWorklistQueryService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** UC-46 worklist of a service room: the SERVICE queue with patient header and the orders for the room. */
@RestController
public class ServiceWorklistController {
    private final ServiceWorklistQueryService worklist;

    public ServiceWorklistController(ServiceWorklistQueryService worklist) { this.worklist = worklist; }

    @GetMapping("/api/v1/queue/rooms/{roomId}/service-worklist")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_IMAGING_TECHNICIAN')")
    public ResponseEntity<ServiceWorklistDtos.Worklist> get(@PathVariable UUID roomId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(worklist.worklist(roomId, actor));
    }
}
