package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos;
import fu.fa26.sep490.g58.otcms_be.visit.service.AttendingDoctorTransferService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceOrderRoomTransferService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReturnService;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInRoomSuggestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Visit lifecycle for the receptionist: return within 7 days, doctor and room transfers, walk-in room suggestion. */
@RestController
public class VisitLifecycleController {
    private final VisitReturnService returns;
    private final AttendingDoctorTransferService doctorTransfers;
    private final ServiceOrderRoomTransferService roomTransfers;
    private final WalkInRoomSuggestionService suggestions;

    public VisitLifecycleController(VisitReturnService returns, AttendingDoctorTransferService doctorTransfers,
            ServiceOrderRoomTransferService roomTransfers, WalkInRoomSuggestionService suggestions) {
        this.returns = returns;
        this.doctorTransfers = doctorTransfers;
        this.roomTransfers = roomTransfers;
        this.suggestions = suggestions;
    }

    /** The visit this patient may return to without a new fee; 204 when there is none (a new visit is needed). */
    @GetMapping("/api/v1/reception/patients/{patientProfileId}/return-candidate")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<VisitLifecycleDtos.ReturnCandidate> returnCandidate(@PathVariable UUID patientProfileId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return returns.findCandidate(patientProfileId, actor)
                .map(candidate -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(candidate))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @PostMapping("/api/v1/visits/{visitId}/returns")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public VisitLifecycleDtos.Updated<VisitLifecycleDtos.VisitQueueTicket> returnToVisit(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest request) {
        return VisitLifecycleDtos.Updated.of(returns.returnToVisit(visitId, actor, CorrelationIdFilter.current(request)));
    }

    @PostMapping("/api/v1/visits/{visitId}/attending-doctor-transfers")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public VisitLifecycleDtos.Updated<VisitLifecycleDtos.VisitQueueTicket> transferDoctor(@PathVariable UUID visitId,
            @Valid @RequestBody VisitLifecycleDtos.AttendingDoctorTransferRequest body,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest request) {
        return VisitLifecycleDtos.Updated.of(doctorTransfers.transfer(visitId, body.doctorProfileId(), body.reason(),
                actor, CorrelationIdFilter.current(request)));
    }

    @PostMapping("/api/v1/service-orders/{orderId}/room-transfers")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public VisitLifecycleDtos.Updated<VisitLifecycleDtos.ServiceOrderRoomTransfer> transferRoom(
            @PathVariable UUID orderId, @Valid @RequestBody VisitLifecycleDtos.ServiceOrderRoomTransferRequest body,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest request) {
        return VisitLifecycleDtos.Updated.of(roomTransfers.transfer(orderId, body.newRoomId(), body.reason(), actor,
                CorrelationIdFilter.current(request)));
    }

    /** Examination rooms with a doctor on shift, fewest waiting first; the first item is the default room. */
    @GetMapping("/api/v1/reception/walk-in-suggestions")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<VisitLifecycleDtos.WalkInSuggestions> walkInSuggestions(
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(suggestions.suggest(actor));
    }
}
