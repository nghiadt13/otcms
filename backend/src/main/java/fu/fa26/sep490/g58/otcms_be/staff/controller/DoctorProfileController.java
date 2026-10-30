package fu.fa26.sep490.g58.otcms_be.staff.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.DoctorProfileRequest;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.DoctorIntervalRequest;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.DoctorDirectoryStateRequest;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.DoctorMutationImpactRequest;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorAdminResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorItems;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorPublicResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.ShiftItems;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorDirectoryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.staff.service.ProvisionDoctorProfileService.Command;
import fu.fa26.sep490.g58.otcms_be.staff.service.ProvisionDoctorProfileService;
import fu.fa26.sep490.g58.otcms_be.staff.service.ManageDoctorResourcesService;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class DoctorProfileController {
    private final DoctorDirectoryService directory;
    private final DoctorScheduleService schedules;
    private final ProvisionDoctorProfileService provision;
    private final DoctorProfileMapper mapper;
    private final ManageDoctorResourcesService manageResources;

    public DoctorProfileController(DoctorDirectoryService directory, DoctorScheduleService schedules,
                                   ProvisionDoctorProfileService provision, DoctorProfileMapper mapper,
                                   ManageDoctorResourcesService manageResources) {
        this.directory = directory; this.schedules = schedules; this.provision = provision; this.mapper = mapper;
        this.manageResources = manageResources;
    }

    @GetMapping("/api/v1/doctors")
    public DoctorItems publicDirectory() {
        return mapper.toItems(directory.listPublic());
    }

    @GetMapping("/api/v1/doctors/{doctorProfileId}")
    public ResponseEntity<DoctorPublicResponse> publicProfile(@PathVariable UUID doctorProfileId) {
        return directory.findPublic(doctorProfileId).map(mapper::toPublicResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/doctors/me/shifts")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ShiftItems ownShifts(@AuthenticationPrincipal AuthenticatedAccount actor,
                                @RequestParam Instant from, @RequestParam Instant to) {
        return mapper.toShiftItems(schedules.forAccount(actor.accountId(), from, to));
    }

    @GetMapping("/api/v1/admin/doctors/{doctorProfileId}/shifts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ShiftItems doctorShifts(@PathVariable UUID doctorProfileId,
                                   @RequestParam Instant from, @RequestParam Instant to) {
        return mapper.toShiftItems(schedules.forProfile(doctorProfileId, from, to));
    }

    @PostMapping("/api/v1/admin/doctor-profiles")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<DoctorAdminResponse> provision(@Valid @RequestBody DoctorProfileRequest request) {
        var profile = provision.provision(new ProvisionDoctorProfileService.Command(request.accountId(),
                request.fullName(), request.photoUrl(), request.workContact(), request.specialties(),
                request.qualifications(), request.biography(), request.consultationInformation()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAdminResponse(profile));
    }

    @PostMapping("/api/v1/admin/doctors/{doctorProfileId}/shifts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse> createShift(
            @PathVariable UUID doctorProfileId, @Valid @RequestBody DoctorIntervalRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        var result = manageResources.createShift(doctorProfileId, request.getStartsAt(), request.getEndsAt(),
                actor, CorrelationIdFilter.current(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/api/v1/admin/doctors/{doctorProfileId}/shifts/{shiftId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShift(@PathVariable UUID doctorProfileId, @PathVariable UUID shiftId,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        manageResources.deleteShift(doctorProfileId, shiftId, actor, CorrelationIdFilter.current(http));
    }

    @PutMapping("/api/v1/admin/doctors/{doctorProfileId}/shifts/{shiftId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse updateShift(
            @PathVariable UUID doctorProfileId, @PathVariable UUID shiftId,
            @Valid @RequestBody DoctorIntervalRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        return manageResources.updateShift(doctorProfileId, shiftId, request.getStartsAt(), request.getEndsAt(),
                actor, CorrelationIdFilter.current(http));
    }

    @PostMapping("/api/v1/admin/doctors/{doctorProfileId}/time-offs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse> createTimeOff(
            @PathVariable UUID doctorProfileId, @Valid @RequestBody DoctorIntervalRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        var result = manageResources.createTimeOff(doctorProfileId, request.getStartsAt(), request.getEndsAt(),
                actor, CorrelationIdFilter.current(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/api/v1/admin/doctors/{doctorProfileId}/time-offs/{timeOffId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimeOff(@PathVariable UUID doctorProfileId, @PathVariable UUID timeOffId,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        manageResources.deleteTimeOff(doctorProfileId, timeOffId, actor, CorrelationIdFilter.current(http));
    }

    @GetMapping("/api/v1/admin/doctors/{doctorProfileId}/time-offs/{timeOffId}/mutation-impact")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public AdminMutationImpact assessTimeOffRemovalImpact(@PathVariable UUID doctorProfileId,
            @PathVariable UUID timeOffId, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return manageResources.assessTimeOffRemovalImpact(doctorProfileId, timeOffId, actor);
    }

    @PutMapping("/api/v1/admin/doctors/{doctorProfileId}/time-offs/{timeOffId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse updateTimeOff(
            @PathVariable UUID doctorProfileId, @PathVariable UUID timeOffId,
            @Valid @RequestBody DoctorIntervalRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        return manageResources.updateTimeOff(doctorProfileId, timeOffId, request.getStartsAt(), request.getEndsAt(),
                actor, CorrelationIdFilter.current(http));
    }

    @PatchMapping("/api/v1/admin/doctors/{doctorProfileId}/directory-state")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public DoctorAdminResponse updateDirectoryState(@PathVariable UUID doctorProfileId,
            @Valid @RequestBody DoctorDirectoryStateRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        var result = manageResources.updateDirectoryState(doctorProfileId, request.isPubliclyVisible(),
                request.isAcceptingNewBookings(), request.isInActiveDirectory(), actor,
                CorrelationIdFilter.current(http));
        return mapper.toAdminResponse(result);
    }

    @PostMapping("/api/v1/admin/doctors/{doctorProfileId}/mutation-impact")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public AdminMutationImpact assessMutationImpact(@PathVariable UUID doctorProfileId,
            @Valid @RequestBody DoctorMutationImpactRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return manageResources.assessImpact(doctorProfileId, request.getResourceType(), request.getResourceId(),
                request.getStartsAt(), request.getEndsAt(), actor);
    }
}
