package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.visit.mapper.VisitRoomMapper;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitRoomsResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffRoomQueryController {
    private final ManageVisitRoomService rooms;

    public StaffRoomQueryController(ManageVisitRoomService rooms) { this.rooms = rooms; }

    @GetMapping("/api/v1/staff/rooms")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST', 'ROLE_DOCTOR', 'ROLE_ADMIN', 'ROLE_OWNER', 'ROLE_LAB_TECHNICIAN', 'ROLE_IMAGING_TECHNICIAN')")
    @Operation(summary = "Read active and inactive rooms for clinic staff",
            description = "Existing room projection ordered by roomCode then id. No search, filter or pagination parameters.")
    public VisitRoomsResponse list() { return VisitRoomMapper.toItemsResponse(rooms.listAll()); }
}
