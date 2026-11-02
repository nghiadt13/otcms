package fu.fa26.sep490.g58.otcms_be.patient.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientProfileException;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.PatientProfileMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.CreatePatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.ReceptionVerificationProfileResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patient-profiles")
public class PatientProfileController {
    private final PatientProfileService profiles;
    private final PatientProfileMapper mapper;

    public PatientProfileController(PatientProfileService profiles, PatientProfileMapper mapper) { this.profiles = profiles; this.mapper = mapper; }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public Page<PatientProfileResponse> search(@RequestParam String query,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        requireReceptionist();
        return profiles.search(query, page, size).map(mapper::toResponse);
    }

    @GetMapping("/{patientProfileId}/reception-verification")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<ReceptionVerificationProfileResponse> getReceptionVerificationProfile(
            @PathVariable java.util.UUID patientProfileId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccount actor))
            throw new PatientProfileException("FORBIDDEN");
        var profile = profiles.getReceptionVerificationProfile(actor.accountId(), patientProfileId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ReceptionVerificationProfileResponse(profile.patientProfileId(), profile.fullName(), profile.dateOfBirth()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<PatientProfileResponse> create(@Valid @RequestBody CreatePatientProfileRequest request) {
        requireReceptionist();
        PatientProfile saved = profiles.create(request.fullName(), request.phone(), request.phoneOwnerType(),
                request.dateOfBirth(), request.gender());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(saved));
    }

    private void requireReceptionist() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccount actor)
                || !"RECEPTIONIST".equals(actor.accountRole().name()))
            throw new PatientProfileException("FORBIDDEN");
    }

}
