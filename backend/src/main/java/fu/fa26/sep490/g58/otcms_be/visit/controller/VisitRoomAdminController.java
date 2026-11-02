package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.visit.mapper.VisitRoomMapper;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.request.CreateVisitRoomRequest;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.request.UpdateVisitRoomRequest;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitRoomResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitRoomsResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class VisitRoomAdminController {
    private final ManageVisitRoomService rooms;

    public VisitRoomAdminController(ManageVisitRoomService rooms) { this.rooms = rooms; }

    @GetMapping("/api/v1/admin/visit-rooms")
    public VisitRoomsResponse list() {
        return VisitRoomMapper.toItemsResponse(rooms.listAll());
    }

    @GetMapping("/api/v1/admin/visit-rooms/{roomId}")
    public VisitRoomResponse detail(@PathVariable UUID roomId) {
        return VisitRoomMapper.toResponse(rooms.get(roomId));
    }

    @PostMapping("/api/v1/admin/visit-rooms")
    public ResponseEntity<VisitRoomResponse> create(@Valid @RequestBody CreateVisitRoomRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        var room = rooms.create(body.getRoomCode(), body.getDisplayLabel(), body.getType(), body.getDescription(),
                body.getServiceIds(), body.getCapabilities(),
                account.accountId(), CorrelationIdFilter.current(request));
        return ResponseEntity.created(URI.create("/api/v1/admin/visit-rooms/" + room.id()))
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(VisitRoomMapper.toResponse(room));
    }

    @PutMapping("/api/v1/admin/visit-rooms/{roomId}")
    public ResponseEntity<VisitRoomResponse> update(@PathVariable UUID roomId,
            @Valid @RequestBody UpdateVisitRoomRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        var room = rooms.update(roomId, body.getDisplayLabel(), body.getDescription(), body.getServiceIds(),
                body.getCapabilities(),
                account.accountId(), CorrelationIdFilter.current(request));
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(VisitRoomMapper.toResponse(room));
    }

    @DeleteMapping("/api/v1/admin/visit-rooms/{roomId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID roomId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        rooms.deactivate(roomId, account.accountId(), CorrelationIdFilter.current(request));
        return ResponseEntity.noContent().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).build();
    }

    @PostMapping("/api/v1/admin/visit-rooms/{roomId}/activation")
    public ResponseEntity<Void> activate(@PathVariable UUID roomId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        rooms.activate(roomId, account.accountId(), CorrelationIdFilter.current(request));
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).build();
    }
}
