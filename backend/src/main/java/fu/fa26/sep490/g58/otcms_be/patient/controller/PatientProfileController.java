package fu.fa26.sep490.g58.otcms_be.patient.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientProfileException;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.PatientProfileMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.CreatePatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.ReceptionVerificationProfileResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileMatchesResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileDetailResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.UpdatePatientProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(summary = "Create a walk-in administrative profile without an account",
            description = "Address is required. nationalId is optional; when present identityDocumentType is required. "
                    + "400 VALIDATION_FAILED (MSG-17); 409 PATIENT_PROFILE_DUPLICATE (MSG-18); 403 ACCESS_DENIED (MSG-19).")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Profile created"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED"),
            @ApiResponse(responseCode = "409", description = "PATIENT_PROFILE_DUPLICATE"),
            @ApiResponse(responseCode = "500", description = "MSG-04")})
    public ResponseEntity<PatientProfileResponse> create(@Valid @RequestBody CreatePatientProfileRequest request) {
        requireReceptionist();
        PatientProfile saved = profiles.create(actorId(), request.fullName(), request.phone(), request.phoneOwnerType(),
                request.dateOfBirth(), request.gender(), request.address(), request.nationalId(), request.identityDocumentType());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(saved));
    }

    @GetMapping("/matches")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    @Operation(summary = "Match administrative profiles by exact canonical phone and birth date",
            description = "fullName refines only multiple candidates, using normalized exact case-insensitive equality. "
                    + "Zero matches returns empty items. Remaining ambiguity returns 409 PATIENT_MATCH_AMBIGUOUS (MSG-18).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED / MSG-17"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED / MSG-19"),
            @ApiResponse(responseCode = "409", description = "PATIENT_MATCH_AMBIGUOUS / MSG-18"),
            @ApiResponse(responseCode = "500", description = "MSG-04")})
    public ResponseEntity<PatientProfileMatchesResponse> matches(@RequestParam String phone,
            @RequestParam java.time.LocalDate dateOfBirth, @RequestParam(required = false) String fullName) {
        var items = profiles.matches(actorId(), phone, dateOfBirth, fullName).stream().map(mapper::toSummary).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PatientProfileMatchesResponse(items));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    @Operation(summary = "Read administrative patient profile fields only")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED / MSG-17"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED / MSG-19"),
            @ApiResponse(responseCode = "404", description = "PATIENT_PROFILE_NOT_FOUND / MSG-19"),
            @ApiResponse(responseCode = "500", description = "MSG-04")})
    public ResponseEntity<PatientProfileDetailResponse> detail(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.toDetail(profiles.detail(actorId(), id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    @Operation(summary = "Update administrative fields using optimistic version",
            description = "Account linkage is preserved. Legacy address may remain null. Stale version: 409 STALE_VERSION (MSG-16).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED / MSG-17"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED / MSG-19"),
            @ApiResponse(responseCode = "404", description = "PATIENT_PROFILE_NOT_FOUND / MSG-19"),
            @ApiResponse(responseCode = "409", description = "STALE_VERSION / MSG-16"),
            @ApiResponse(responseCode = "500", description = "MSG-04")})
    public ResponseEntity<PatientProfileDetailResponse> update(@PathVariable java.util.UUID id,
            @Valid @RequestBody UpdatePatientProfileRequest request) {
        var result = profiles.update(actorId(), id, request.fullName(), request.phone(), request.phoneOwnerType(),
                request.dateOfBirth(), request.gender(), request.address(), request.nationalId(),
                request.identityDocumentType(), request.version());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.toDetail(result));
    }

    private java.util.UUID actorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccount actor))
            throw new PatientProfileException("ACCESS_DENIED");
        return actor.accountId();
    }

    private void requireReceptionist() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccount actor)
                || !"RECEPTIONIST".equals(actor.accountRole().name()))
            throw new PatientProfileException("FORBIDDEN");
    }

}
