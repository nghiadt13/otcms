package fu.fa26.sep490.g58.otcms_be.patient.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.PatientProfileMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.UpdatePatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileDetailResponse;
import fu.fa26.sep490.g58.otcms_be.patient.service.MyPatientProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/patient-profile")
@PreAuthorize("hasAuthority('ROLE_PATIENT')")
public class MyPatientProfileController {
    private final MyPatientProfileService profiles;
    private final PatientProfileMapper mapper;
    public MyPatientProfileController(MyPatientProfileService profiles, PatientProfileMapper mapper) {
        this.profiles = profiles; this.mapper = mapper;
    }
    @GetMapping
    public ResponseEntity<PatientProfileDetailResponse> get(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(mapper.toDetail(profiles.get(actor)));
    }
    @PutMapping
    public ResponseEntity<PatientProfileDetailResponse> update(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody UpdatePatientProfileRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(mapper.toDetail(profiles.update(actor, request)));
    }
}
