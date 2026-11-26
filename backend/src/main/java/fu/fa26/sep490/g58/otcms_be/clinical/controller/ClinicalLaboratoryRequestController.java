package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.LaboratoryRequestDtos;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalLaboratoryRequestService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalLaboratoryDispatchService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class ClinicalLaboratoryRequestController {
    private final ClinicalLaboratoryRequestService requests;
    private final ClinicalDoctorIdentityResolver doctors;
    private final ClinicalLaboratoryDispatchService dispatch;
    private final fu.fa26.sep490.g58.otcms_be.clinical.service.LaboratoryQueueService queue;

    public ClinicalLaboratoryRequestController(ClinicalLaboratoryRequestService requests, ClinicalDoctorIdentityResolver doctors,
            ClinicalLaboratoryDispatchService dispatch, fu.fa26.sep490.g58.otcms_be.clinical.service.LaboratoryQueueService queue) {
        this.requests = requests;
        this.doctors = doctors;
        this.dispatch = dispatch;
        this.queue = queue;
    }

    @GetMapping("/api/v1/laboratory/orders")
    @PreAuthorize("hasAuthority('ROLE_LAB_TECHNICIAN')")
    public ResponseEntity<java.util.Map<String, java.util.List<fu.fa26.sep490.g58.otcms_be.clinical.service.LaboratoryQueueService.Item>>> queue(
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("items", queue.today(account)));
    }

    @GetMapping("/api/v1/laboratory/orders/history")
    @PreAuthorize("hasAuthority('ROLE_LAB_TECHNICIAN')")
    public ResponseEntity<java.util.Map<String, java.util.List<fu.fa26.sep490.g58.otcms_be.clinical.service.LaboratoryQueueService.Item>>> history(
            @AuthenticationPrincipal AuthenticatedAccount account) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("items", queue.history(account)));
    }

    @PostMapping("/api/v1/visits/{visitId}/laboratory-requests")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<ClinicalOrderingService.RoundView> submit(@PathVariable UUID visitId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody LaboratoryRequestDtos.Request body,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        var result = requests.submit(new ClinicalLaboratoryRequestService.SubmitRequest(visitId,
                doctors.requireDoctorProfile(account), key, Boolean.TRUE.equals(body.patientConsentConfirmed()),
                body.orders().stream().map(item -> new ClinicalLaboratoryRequestService.OrderRequest(
                        item.serviceId(), item.clinicalReason(), item.instructions())).toList()));
        return ResponseEntity.status(result.replayed() ? 200 : 201).cacheControl(CacheControl.noStore()).body(result.round());
    }

    public record Assignment(@jakarta.validation.constraints.NotNull UUID roomId) { }

    public record Rooms(java.util.List<fu.fa26.sep490.g58.otcms_be.visit.service.LaboratoryRoomQueryService.Room> items) { }

    @GetMapping("/api/v1/reception/clinical-service-orders/{orderId}/eligible-laboratory-rooms")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<Rooms> eligibleRooms(@PathVariable UUID orderId, @AuthenticationPrincipal AuthenticatedAccount account) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Rooms(dispatch.eligibleRooms(orderId, account)));
    }

    @PostMapping("/api/v1/reception/clinical-service-orders/{orderId}/laboratory-dispatch")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<Void> dispatch(@PathVariable UUID orderId, @Valid @RequestBody Assignment body,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        dispatch.assign(orderId, body.roomId(), account);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
