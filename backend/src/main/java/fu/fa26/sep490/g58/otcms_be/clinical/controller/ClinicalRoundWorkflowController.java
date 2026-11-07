package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalRoundWorkflowService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClinicalRoundWorkflowController {
    private final ClinicalRoundWorkflowService workflow;
    private final ClinicalDoctorIdentityResolver doctors;
    public ClinicalRoundWorkflowController(ClinicalRoundWorkflowService workflow, ClinicalDoctorIdentityResolver doctors) {
        this.workflow = workflow; this.doctors = doctors;
    }
    public record OrderPage(List<ClinicalRoundWorkflowService.Order> items) { }
    public record DispatchBody(@jakarta.validation.constraints.NotEmpty
            @jakarta.validation.constraints.Size(max = 200)
            @jakarta.validation.Valid List<ClinicalRoundWorkflowService.RoomAssignment> assignments) { }
    public record RoomOptionsPage(List<ClinicalRoundWorkflowService.RoomOptions> items) { }
    public record DispatchResponse(List<UUID> diagnosticRequestIds) { }
    @GetMapping("/api/v1/visits/{visitId}/imaging-orders")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<OrderPage> doctorOrders(@PathVariable UUID visitId, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new OrderPage(
                workflow.listDoctorOrders(visitId, doctors.requireDoctorProfile(actor))));
    }
    @GetMapping("/api/v1/reception/clinical-ordering-rounds")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<ClinicalRoundWorkflowService.RoundPage> queue(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workflow.listReceptionRounds(actor, page, size));
    }
    @GetMapping("/api/v1/reception/clinical-service-orders/{serviceOrderId}/eligible-imaging-rooms")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<ClinicalRoundWorkflowService.RoomOptions> rooms(@PathVariable UUID serviceOrderId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(workflow.eligibleImagingRooms(List.of(serviceOrderId), actor).getFirst());
    }
    @GetMapping("/api/v1/reception/clinical-service-orders/eligible-imaging-rooms")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<RoomOptionsPage> roomsBatch(@RequestParam List<UUID> serviceOrderIds,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RoomOptionsPage(workflow.eligibleImagingRooms(serviceOrderIds, actor)));
    }
    @PostMapping("/api/v1/reception/clinical-ordering-rounds/{roundId}/imaging-dispatch")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<DispatchResponse> dispatch(@PathVariable UUID roundId, @AuthenticationPrincipal AuthenticatedAccount actor,
            @jakarta.validation.Valid @RequestBody DispatchBody body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new DispatchResponse(workflow.dispatch(roundId, actor, body.assignments())));
    }
}
