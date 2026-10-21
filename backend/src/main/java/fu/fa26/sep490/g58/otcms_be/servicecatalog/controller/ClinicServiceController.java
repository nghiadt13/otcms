package fu.fa26.sep490.g58.otcms_be.servicecatalog.controller;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.request.ServiceRequest;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.ItemsResponse;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.ServiceResponse;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ActivateClinicServiceService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.CreateClinicServiceService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.DeactivateClinicServiceService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.UpdateClinicServiceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ManageDoctorServiceAssignmentsService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.request.DoctorAssignmentRequest;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.DoctorAssignmentResponse;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;

@RestController
public class ClinicServiceController {
    private final CreateClinicServiceService create;
    private final ActivateClinicServiceService activate;
    private final UpdateClinicServiceService update;
    private final DeactivateClinicServiceService deactivate;
    private final GetClinicServiceCatalogService catalog;
    private final ClinicServiceMapper mapper;
    private final ManageDoctorServiceAssignmentsService assignments;

    public ClinicServiceController(CreateClinicServiceService create, ActivateClinicServiceService activate, UpdateClinicServiceService update,
                                   DeactivateClinicServiceService deactivate, GetClinicServiceCatalogService catalog,
                                   ClinicServiceMapper mapper, ManageDoctorServiceAssignmentsService assignments) {
        this.create = create; this.activate = activate; this.update = update; this.deactivate = deactivate;
        this.catalog = catalog; this.mapper = mapper; this.assignments = assignments;
    }

    @GetMapping("/api/v1/clinic-services")
    public ItemsResponse publicCatalog() {
        return mapper.toItemsResponse(catalog.publicCatalog());
    }

    @GetMapping("/api/v1/staff/clinic-services")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST', 'ROLE_DOCTOR')")
    public ItemsResponse staffCatalog() {
        return mapper.toItemsResponse(catalog.staffCatalog());
    }

    @PostMapping("/api/v1/admin/clinic-services")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceRequest request) {
        ClinicService service = create.create(request.name(), request.description(), request.durationMinutes(),
                request.bufferMinutes(), request.onlineBookable());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(service));
    }

    @PutMapping("/api/v1/admin/clinic-services/{serviceId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ServiceResponse update(@PathVariable UUID serviceId, @Valid @RequestBody ServiceRequest request) {
        return mapper.toResponse(update.update(serviceId, request.name(), request.description(),
                request.durationMinutes(), request.bufferMinutes(), request.onlineBookable()));
    }

    @DeleteMapping("/api/v1/admin/clinic-services/{serviceId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID serviceId, @AuthenticationPrincipal AuthenticatedAccount actor,
                           HttpServletRequest request) {
        deactivate.deactivate(serviceId, actor, CorrelationIdFilter.current(request));
    }

    @PostMapping("/api/v1/admin/clinic-services/{serviceId}/activation")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable UUID serviceId, @AuthenticationPrincipal AuthenticatedAccount actor,
            HttpServletRequest request) {
        activate.activate(serviceId, actor, CorrelationIdFilter.current(request));
    }

    @PostMapping("/api/v1/admin/clinic-services/{serviceId}/doctor-assignments")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<DoctorAssignmentResponse> assignDoctor(@PathVariable UUID serviceId,
            @Valid @RequestBody DoctorAssignmentRequest request,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        var response = assignments.assign(serviceId, request.getDoctorProfileId(), actor,
                CorrelationIdFilter.current(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/api/v1/admin/clinic-services/{serviceId}/doctor-assignments/{assignmentId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDoctorAssignment(@PathVariable UUID serviceId, @PathVariable UUID assignmentId,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest http) {
        assignments.remove(serviceId, assignmentId, actor, CorrelationIdFilter.current(http));
    }

    @GetMapping("/api/v1/admin/clinic-services/{serviceId}/doctor-assignments/{assignmentId}/impact")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public AdminMutationImpact assignmentImpact(@PathVariable UUID serviceId, @PathVariable UUID assignmentId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return assignments.assessRemovalImpact(serviceId, assignmentId, actor);
    }

}
