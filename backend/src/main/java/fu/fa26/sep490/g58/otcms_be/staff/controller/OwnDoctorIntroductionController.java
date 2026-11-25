package fu.fa26.sep490.g58.otcms_be.staff.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.DoctorIntroduction;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.OwnDoctorIntroductionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/doctor-introduction")
@PreAuthorize("hasAuthority('ROLE_DOCTOR')")
public class OwnDoctorIntroductionController {
    private final OwnDoctorIntroductionService service;

    public OwnDoctorIntroductionController(OwnDoctorIntroductionService service) {
        this.service = service;
    }

    @GetMapping
    public DoctorIntroduction get(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return service.get(actor.accountId());
    }

    @PutMapping
    public DoctorIntroduction update(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody DoctorIntroduction request) {
        return service.update(actor.accountId(), request);
    }
}
