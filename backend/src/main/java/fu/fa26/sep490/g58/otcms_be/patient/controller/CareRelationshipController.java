package fu.fa26.sep490.g58.otcms_be.patient.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareRelationshipException;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.service.ConfirmCareRelationshipService;
import fu.fa26.sep490.g58.otcms_be.patient.service.RequestCareRelationshipService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Deliberately not a Spring MVC controller under approved FEAT-002 D03: family
// relationship requests do not grant appointment authority. Family record
// sharing needs its own reviewed authorization contract before any API exists.
@RequestMapping("/api/v1/care-relationships")
public class CareRelationshipController {
    private final RequestCareRelationshipService requestRelationship;
    private final ConfirmCareRelationshipService confirmRelationship;
    public CareRelationshipController(RequestCareRelationshipService requestRelationship,
                                      ConfirmCareRelationshipService confirmRelationship) {
        this.requestRelationship = requestRelationship;
        this.confirmRelationship = confirmRelationship;
    }
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public ResponseEntity<CareRelationshipResponse> request(Authentication authentication,
                                                          @Valid @RequestBody RequestCareRelationshipRequest request) {
        var relation = requestRelationship.request(actor(authentication), request.dependentPatientProfileId());
        return ResponseEntity.created(URI.create("/api/v1/care-relationships/" + relation.id()))
                .body(CareRelationshipResponse.from(relation));
    }
    @PostMapping("/{relationshipId}/confirm")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public CareRelationshipResponse confirm(Authentication authentication, @PathVariable UUID relationshipId) {
        return CareRelationshipResponse.from(confirmRelationship.confirm(actor(authentication), relationshipId));
    }
    private UUID actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccount actor)
                || !"PATIENT".equals(actor.accountRole().name())) throw new CareRelationshipException("FORBIDDEN");
        return actor.accountId();
    }
        public static class RequestCareRelationshipRequest {
    private final @NotNull UUID dependentPatientProfileId;
    public RequestCareRelationshipRequest(@NotNull UUID dependentPatientProfileId) {
        this.dependentPatientProfileId = dependentPatientProfileId;
    }
    public UUID getDependentPatientProfileId() { return dependentPatientProfileId; }
    public UUID dependentPatientProfileId() { return dependentPatientProfileId; }
}
        public static class CareRelationshipResponse {
    private final UUID relationshipId;
    private final UUID dependentPatientProfileId;
    private final String state;
    private final Instant requestedAt;
    private final Instant confirmedAt;
    private final Instant revokedAt;
    public CareRelationshipResponse(UUID relationshipId, UUID dependentPatientProfileId, String state, Instant requestedAt, Instant confirmedAt, Instant revokedAt) {
        this.relationshipId = relationshipId;
        this.dependentPatientProfileId = dependentPatientProfileId;
        this.state = state;
        this.requestedAt = requestedAt;
        this.confirmedAt = confirmedAt;
        this.revokedAt = revokedAt;
    }
    public UUID getRelationshipId() { return relationshipId; }
    public UUID relationshipId() { return relationshipId; }
    public UUID getDependentPatientProfileId() { return dependentPatientProfileId; }
    public String getState() { return state; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getRevokedAt() { return revokedAt; }
static CareRelationshipResponse from(CareRelationship relationship) {
            return new CareRelationshipResponse(relationship.id(), relationship.dependentPatientProfileId(),
                    relationship.state().name(), relationship.requestedAt(), relationship.confirmedAt(), relationship.revokedAt());
        }
}
}
