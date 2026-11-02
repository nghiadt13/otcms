package fu.fa26.sep490.g58.otcms_be.patient.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.MedicalHistoryDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientMedicalHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patient-profiles/{id}/medical-history")
@PreAuthorize("hasAnyAuthority('ROLE_PATIENT','ROLE_DOCTOR')")
@ApiResponses({@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED / MSG-20"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED / MSG-19"),
        @ApiResponse(responseCode = "404", description = "PATIENT_PROFILE_NOT_FOUND / MSG-19"),
        @ApiResponse(responseCode = "409", description = "STALE_VERSION / MSG-16"),
        @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE"),
        @ApiResponse(responseCode = "500", description = "MSG-04")})
public class PatientMedicalHistoryController {
    private final PatientMedicalHistoryService histories;
    public PatientMedicalHistoryController(PatientMedicalHistoryService histories) { this.histories = histories; }

    @GetMapping
    @Operation(summary = "Read medical history and allergies", description = "Active PATIENT self/confirmed dependent or active DOCTOR. "
            + "Temporary R3 doctor relation policy. No stored row returns empty strings and version 0 without inserting.")
    public ResponseEntity<MedicalHistoryResponse> get(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(histories.get(actor, id));
    }

    @PutMapping
    @Operation(summary = "Replace all five medical-history texts", description = "CSRF required. Texts are trimmed, then limited to 2000 UTF-16 units. "
            + "Version 0 creates version 1; matching positive version increments; stale versions return 409. Actor/time are server-owned.")
    public ResponseEntity<MedicalHistoryResponse> update(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID id, @Valid @RequestBody UpdateMedicalHistoryRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(histories.update(actor, id, request));
    }
}
