package fu.fa26.sep490.g58.otcms_be.staff.controller;

import fu.fa26.sep490.g58.otcms_be.staff.service.AdminDoctorProfileService;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.DoctorProfileEditRequest;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorAdminResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService.DoctorAccountOption;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/doctor-profiles")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminDoctorProfileController {
    private final AdminDoctorProfileService service;
    private final DoctorProfileMapper mapper;
    public AdminDoctorProfileController(AdminDoctorProfileService service, DoctorProfileMapper mapper) {
        this.service = service; this.mapper = mapper;
    }
    public record Items<T>(List<T> items) { }

    @GetMapping
    public Items<DoctorAdminResponse> list() {
        return new Items<>(service.list().stream().map(mapper::toAdminResponse).toList());
    }
    @GetMapping("/eligible-accounts")
    public Items<DoctorAccountOption> eligibleAccounts() { return new Items<>(service.eligibleAccounts()); }

    @PutMapping("/{doctorId}")
    public DoctorAdminResponse update(@PathVariable UUID doctorId, @Valid @RequestBody DoctorProfileEditRequest request) {
        return mapper.toAdminResponse(service.update(doctorId, new AdminDoctorProfileService.ProfileEdit(request.fullName(),
                request.photoUrl(), request.workContact(), request.specialties(), request.qualifications(),
                request.biography(), request.consultationInformation())));
    }
    @GetMapping("/{doctorId}/time-offs")
    public Items<DoctorIntervalResponse> timeOffs(@PathVariable UUID doctorId, @RequestParam Instant from, @RequestParam Instant to) {
        return new Items<>(service.timeOffs(doctorId, from, to));
    }
}
