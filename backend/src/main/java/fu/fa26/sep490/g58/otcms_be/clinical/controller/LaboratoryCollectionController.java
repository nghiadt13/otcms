package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.service.LaboratoryCollectionService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/laboratory/orders/{orderId}")
@PreAuthorize("hasAuthority('ROLE_LAB_TECHNICIAN')")
public class LaboratoryCollectionController {
    private final LaboratoryCollectionService service;
    public LaboratoryCollectionController(LaboratoryCollectionService service) { this.service = service; }
    public record CollectionRequest(@NotNull @AssertTrue Boolean identityConfirmed) { }
    public record ApprovalRequest(@NotNull @AssertTrue Boolean checked, @NotNull Boolean critical, String contactedDoctor, java.time.Instant contactedAt) { }
    @PostMapping("/result-approval")
    public ResponseEntity<Void> approve(@PathVariable UUID orderId, @Valid @RequestBody ApprovalRequest request, @AuthenticationPrincipal AuthenticatedAccount actor) {
        service.approve(orderId, actor, request.checked(), request.critical(), request.contactedDoctor(), request.contactedAt());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    public record ResultDraftRequest(@NotNull @Size(min = 1, max = 200) java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> values) { }
    @GetMapping("/result-draft")
    public ResponseEntity<?> readDraft(@PathVariable UUID orderId, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("values", service.readDraft(orderId, actor)));
    }
    @PutMapping("/result-draft")
    public ResponseEntity<?> saveDraft(@PathVariable UUID orderId, @Valid @RequestBody ResultDraftRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("values", service.saveDraft(orderId, actor, request.values())));
    }
    @GetMapping("/samples/latest")
    public ResponseEntity<?> latest(@PathVariable UUID orderId, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.latest(orderId, actor));
    }
    @PostMapping("/samples")
    public ResponseEntity<?> collect(@PathVariable UUID orderId, @Valid @RequestBody CollectionRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.collect(orderId, request.identityConfirmed(), actor));
    }
}
