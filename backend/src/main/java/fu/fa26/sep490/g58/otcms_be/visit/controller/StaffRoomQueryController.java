package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.mapper.VisitRoomMapper;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitRoomResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitRoomsResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.StaffRoomScopeService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffRoomQueryController {
    private final StaffRoomScopeService rooms;

    public StaffRoomQueryController(StaffRoomScopeService rooms) { this.rooms = rooms; }

    @GetMapping("/api/v1/staff/rooms")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST', 'ROLE_DOCTOR', 'ROLE_ADMIN', 'ROLE_OWNER', 'ROLE_LAB_TECHNICIAN', 'ROLE_IMAGING_TECHNICIAN')")
    @Operation(summary = "Read the rooms relevant to the signed-in staff member (UC-64)",
            description = "Admin/owner: all rooms. Receptionist: active rooms. Doctor: rooms of today's shifts and of the "
                    + "service orders of own open visits. Imaging technician: assigned rooms. Ordered by roomCode then id.")
    public ResponseEntity<VisitRoomsResponse> list(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(VisitRoomMapper.toItemsResponse(rooms.listVisible(actor)));
    }

    @GetMapping("/api/v1/staff/rooms/{roomId}")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST', 'ROLE_DOCTOR', 'ROLE_ADMIN', 'ROLE_OWNER', 'ROLE_LAB_TECHNICIAN', 'ROLE_IMAGING_TECHNICIAN')")
    @Operation(summary = "Read one room inside the signed-in staff member's scope (UC-64); MSG-50 when unavailable or out of scope")
    public ResponseEntity<VisitRoomResponse> get(@PathVariable UUID roomId, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(VisitRoomMapper.toResponse(rooms.getVisible(actor, roomId)));
    }
}
