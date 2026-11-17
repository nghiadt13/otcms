package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.QueueRoomDtos;
import fu.fa26.sep490.g58.otcms_be.visit.service.QueueStateMachine;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueueRoomController {
    private final QueueStateMachine queue;

    public QueueRoomController(QueueStateMachine queue) { this.queue = queue; }

    @PostMapping("/api/v1/queue/rooms/{roomId}/call-next")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_DOCTOR')")
    public QueueRoomDtos.Updated callNext(@PathVariable UUID roomId, @RequestParam String stage,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return updated(queue.callNext(roomId, stage, actor));
    }

    @PostMapping("/api/v1/queue/entries/{id}/absence")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_DOCTOR')")
    public QueueRoomDtos.Updated absent(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return updated(queue.markAbsent(id, actor));
    }

    @PostMapping("/api/v1/queue/entries/{id}/return")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public QueueRoomDtos.Updated returnEntry(@PathVariable UUID id, @RequestBody QueueRoomDtos.ReturnRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return updated(queue.returnFromAbsence(id, request.beforeEntryId(), request.reason(), actor));
    }

    @GetMapping("/api/v1/queue/rooms/{roomId}")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_DOCTOR')")
    public ResponseEntity<List<QueueRoomDtos.Entry>> room(@PathVariable UUID roomId, @RequestParam String stage,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queue.list(roomId, stage, actor));
    }

    @GetMapping("/api/v1/public/queue/rooms/{roomId}/now-serving")
    public ResponseEntity<List<Integer>> nowServing(@PathVariable UUID roomId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queue.nowServing(roomId));
    }

    private static QueueRoomDtos.Updated updated(QueueRoomDtos.Entry entry) {
        return new QueueRoomDtos.Updated("MSG-43", entry);
    }
}
